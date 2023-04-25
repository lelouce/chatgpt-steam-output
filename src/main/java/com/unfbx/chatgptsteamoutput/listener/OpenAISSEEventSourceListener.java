package com.unfbx.chatgptsteamoutput.listener;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.ChatCompletionResponse;
import com.unfbx.chatgptsteamoutput.config.ApplicationContextProvider;
import com.unfbx.chatgptsteamoutput.entity.ResponseError;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 描述：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class OpenAISSEEventSourceListener extends EventSourceListener {

    private SseEmitter sseEmitter;

    public OpenAISSEEventSourceListener(SseEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("OpenAI建立sse连接...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("OpenAI返回数据：{}", data);
        if (data.equals("[DONE]")) {
            log.info("OpenAI返回数据结束了");
            sseEmitter.send(SseEmitter.event()
                    .id("[DONE]")
                    .data("[DONE]")
                    .reconnectTime(3000));
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        ChatCompletionResponse completionResponse = mapper.readValue(data, ChatCompletionResponse.class); // 读取Json
        sseEmitter.send(SseEmitter.event()
                .id(completionResponse.getId())
                .data(completionResponse.getChoices().get(0).getDelta())
                .reconnectTime(3000));
    }


    @Override
    public void onClosed(EventSource eventSource) {
        log.info("OpenAI关闭sse连接...");
    }

    final Pattern pattern = Pattern.compile("\\b(sk-\\w+)\\b");

    @SneakyThrows
    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        if (Objects.isNull(response)) {
            return;
        }
        OpenAiStreamClient openAiStreamClient = (OpenAiStreamClient) ApplicationContextProvider.getBean("openAiStreamClient");
        List<String> keyList = openAiStreamClient.getApiKey();
        if (CollectionUtils.isEmpty(keyList)) {
            sseEmitter.send(SseEmitter.event()
                    .id("[KEY EMPTY]")
                    .data("[KEY EMPTY]")
                    .reconnectTime(3000));
        }
        ResponseBody body = response.body();
        if (Objects.nonNull(body)) {
            String str = body.string();
            log.error("OpenAI  sse连接异常data：{}，异常：{}", str, t);
            ObjectMapper objectMapper = new ObjectMapper();
            ResponseError responseError = objectMapper.readValue(str, ResponseError.class);
            if (responseError != null && responseError.getError().getCode().equals("invalid_api_key")) {
                String message = responseError.getError().getMessage();
                if (StringUtils.hasText(message)) {
                    Matcher matcher = pattern.matcher(message);
                    if (matcher.find()) {
                        String apiKey = matcher.group(1);
                        String key = keyList.stream().filter(x -> x.contains(apiKey)).findFirst().orElse("");
                        int i = keyList.indexOf(key);
                        if (i > -1) {
                            openAiStreamClient.getApiKey().remove(keyList.get(i));
                        }
                    }
                }
                sseEmitter.send(SseEmitter.event()
                        .id("[KEY ERROR]")
                        .data("[KEY ERROR]")
                        .reconnectTime(3000));
            }
        } else {
            log.error("OpenAI  sse连接异常data：{}，异常：{}", response, t);
        }
        eventSource.cancel();
    }
}
