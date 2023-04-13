const EventStreamContentType = 'text/event-stream';

const DefaultRetryInterval = 1000;
const LastEventId = 'last-event-id';
// parse.js
async function getBytes(stream, onChunk) {
    const reader = stream.getReader();
    let result;
    while (!(result = await reader.read()).done) {
        onChunk(result.value);
    }
}

const ControlChars = {
    NewLine: 10,
    CarriageReturn: 13,
    Space: 32,
    Colon: 58,
}
function getLines(onLine) {
    let buffer;
    let position; // current read position
    let fieldLength; // length of the `field` portion of the line
    let discardTrailingNewline = false;

    // return a function that can process each incoming byte chunk:
    return function onChunk(arr) {
        if (buffer === undefined) {
            buffer = arr;
            position = 0;
            fieldLength = -1;
        } else {
            // we're still parsing the old line. Append the new bytes into buffer:
            buffer = concat(buffer, arr);
        }

        const bufLength = buffer.length;
        let lineStart = 0; // index where the current line starts
        while (position < bufLength) {
            if (discardTrailingNewline) {
                if (buffer[position] === ControlChars.NewLine) {
                    lineStart = ++position; // skip to next char
                }
                
                discardTrailingNewline = false;
            }
            
            // start looking forward till the end of line:
            let lineEnd = -1; // index of the \r or \n char
            for (; position < bufLength && lineEnd === -1; ++position) {
                switch (buffer[position]) {
                    case ControlChars.Colon:
                        if (fieldLength === -1) { // first colon in line
                            fieldLength = position - lineStart;
                        }
                        break;
                    // @ts-ignore:7029 \r case below should fallthrough to \n:
                    case ControlChars.CarriageReturn:
                        discardTrailingNewline = true;
                    case ControlChars.NewLine:
                        lineEnd = position;
                        break;
                }
            }

            if (lineEnd === -1) {
                // We reached the end of the buffer but the line hasn't ended.
                // Wait for the next arr and then continue parsing:
                break;
            }

            // we've reached the line end, send it out:
            onLine(buffer.subarray(lineStart, lineEnd), fieldLength);
            lineStart = position; // we're now on the next line
            fieldLength = -1;
        }

        if (lineStart === bufLength) {
            buffer = undefined; // we've finished reading it
        } else if (lineStart !== 0) {
            // Create a new view into buffer beginning at lineStart so we don't
            // need to copy over the previous lines when we get the new arr:
            buffer = buffer.subarray(lineStart);
            position -= lineStart;
        }
    }
}
function getMessages(
    onId,
    onRetry,
    onMessage
) {
    let message = newMessage();
    const decoder = new TextDecoder();

    // return a function that can process each incoming line buffer:
    return function onLine(line, fieldLength) {
        if (line.length === 0) {
            // empty line denotes end of message. Trigger the callback and start a new message:
            onMessage?.(message);
            message = newMessage();
        } else if (fieldLength > 0) { // exclude comments and lines with no values
            // line is of format "<field>:<value>" or "<field>: <value>"
            // https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation
            const field = decoder.decode(line.subarray(0, fieldLength));
            const valueOffset = fieldLength + (line[fieldLength + 1] === ControlChars.Space ? 2 : 1);
            const value = decoder.decode(line.subarray(valueOffset));

            switch (field) {
                case 'data':
                    // if this message already has data, append the new value to the old.
                    // otherwise, just set to the new value:
                    message.data = message.data
                        ? message.data + '\n' + value
                        : value; // otherwise, 
                    break;
                case 'event':
                    message.event = value;
                    break;
                case 'id':
                    onId(message.id = value);
                    break;
                case 'retry':
                    const retry = parseInt(value, 10);
                    if (!isNaN(retry)) { // per spec, ignore non-integers
                        onRetry(message.retry = retry);
                    }
                    break;
            }
        }
    }
}

function concat(a, b) {
    const res = new Uint8Array(a.length + b.length);
    res.set(a);
    res.set(b, a.length);
    return res;
}

function newMessage() {
    // data, event, and id must be initialized to empty strings:
    // https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation
    // retry should be initialized to undefined so we return a consistent shape
    // to the js engine all the time: https://mathiasbynens.be/notes/shapes-ics#takeaways
    return {
        data: '',
        event: '',
        id: '',
        retry: undefined,
    };
}
// fetch.js
function defaultOnOpen(response) {
    const contentType = response.headers.get('content-type');
    if (!contentType?.startsWith(EventStreamContentType)) {
        throw new Error(`Expected content-type to be ${EventStreamContentType}, Actual: ${contentType}`);
    }
}
function fetchEventSource(input, {
    signal: inputSignal,
    headers: inputHeaders,
    onopen: inputOnOpen,
    onmessage,
    onclose,
    onerror,
    openWhenHidden,
    fetch: inputFetch,
    ...rest
}) {
    return new Promise((resolve, reject) => {
        // make a copy of the input headers since we may modify it below:
        const headers = { ...inputHeaders };
        if (!headers.accept) {
            headers.accept = EventStreamContentType;
        }

        let curRequestController;
        function onVisibilityChange() {
            curRequestController.abort(); // close existing request on every visibility change
            if (!document.hidden) {
                create(); // page is now visible again, recreate request.
            }
        }

        if (!openWhenHidden) {
            document.addEventListener('visibilitychange', onVisibilityChange);
        }

        let retryInterval = DefaultRetryInterval;
        let retryTimer = 0;
        function dispose() {
            document.removeEventListener('visibilitychange', onVisibilityChange);
            window.clearTimeout(retryTimer);
            curRequestController.abort();
        }

        // if the incoming signal aborts, dispose resources and resolve:
        inputSignal.closeFetch = () => {
            dispose();
            resolve(); // don't waste time constructing/logging errors
        }
        inputSignal?.addEventListener('abort', () => {
            dispose();
            resolve(); // don't waste time constructing/logging errors
        });

        const fetch = inputFetch ?? window.fetch;
        const onopen = inputOnOpen ?? defaultOnOpen;
        async function create() {
            curRequestController = new AbortController();
            try {
                const response = await fetch(input, {
                    ...rest,
                    headers,
                    signal: curRequestController.signal,
                });

                await onopen(response);
                
                await getBytes(response.body, getLines(getMessages(id => {
                    if (id) {
                        // store the id and send it back on the next retry:
                        headers[LastEventId] = id;
                    } else {
                        // don't send the last-event-id header anymore:
                        delete headers[LastEventId];
                    }
                }, retry => {
                    retryInterval = retry;
                }, onmessage)));

                onclose?.();
                dispose();
                resolve();
            } catch (err) {
                if (!curRequestController.signal.aborted) {
                    // if we haven't aborted the request ourselves:
                    try {
                        // check if we need to retry:
                        const interval = onerror?.(err) ?? retryInterval;
                        window.clearTimeout(retryTimer);
                        retryTimer = window.setTimeout(create, interval);
                    } catch (innerErr) {
                        // we should not retry anymore:
                        dispose();
                        reject(innerErr);
                    }
                }
            }
        }

        create();
    });
}