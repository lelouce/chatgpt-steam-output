package com.unfbx.chatgptsteamoutput;

import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.OpenAiStreamClient.Builder;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 描述：ChatgptSteamOutputApplication
 *
 * @author https:www.unfbx.com
 * @date 2023-02-28
 */
@SpringBootApplication
@Slf4j
public class ChatgptSteamOutputApplication {

    @Value("${chatgpt.apiKey}")
    private List<String> apiKey;
    @Value("${chatgpt.apiHost}")
    private String apiHost;
    @Value("${chatgpt.proxyHost}")
    private String proxyHost;
    @Value("${chatgpt.port}")
    private Integer port;


    public static void main(String[] args) {
        SpringApplication.run(ChatgptSteamOutputApplication.class, args);
    }


    @Bean
    public OpenAiStreamClient openAiStreamClient() {
        Builder builder = OpenAiStreamClient.builder().apiHost(apiHost).apiKey(apiKey);
        log.info("apiKey:{}", apiKey);
        if (proxyHost != null && port != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port));
            OkHttpClient okHttpClient = new OkHttpClient().newBuilder().proxy(proxy).build();
            builder.okHttpClient(okHttpClient);
        }
        return builder.build();
    }

}
