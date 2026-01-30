/**
 * OpenAI GPT API 호출 전담 서비스. 비동기 처리 및 타임아웃/예외 처리.
 * 모든 GPT 호출의 기준 API 역할.
 */
package com.sxxm.stockknock.ai.service;

import com.sxxm.stockknock.ai.dto.AIRequestOptions;
import com.sxxm.stockknock.ai.dto.AIResponseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GPTClientService {

    private static final Logger log = LoggerFactory.getLogger(GPTClientService.class);

    @Value("${gpt.api.key}")
    private String apiKey;

    @Value("${gpt.api.url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String apiUrl;

    @Value("${gpt.model:gemini-flash-latest}")
    private String model;

    /**
     * OpenAiService 인스턴스 생성 (Custom URL 지원)
     */
    private OpenAiService getOpenAiService() {
        try {
            ObjectMapper mapper = OpenAiService.defaultObjectMapper();
            OkHttpClient client = OpenAiService.defaultClient(apiKey, Duration.ofSeconds(60));
            
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(apiUrl)
                    .client(client)
                    .addConverterFactory(JacksonConverterFactory.create(mapper))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();

            OpenAiApi api = retrofit.create(OpenAiApi.class);
            return new OpenAiService(api);
        } catch (Exception e) {
            log.error("OpenAiService 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAiService 초기화 실패", e);
        }
    }

    /**
     * GPT 비동기 응답 생성 (기준 API)
     * 모든 GPT 호출은 이 메서드를 통해 통일된 방식으로 처리됩니다.
     *
     * @param prompt 사용자 프롬프트
     * @param options 호출 옵션 (null이면 기본값 사용)
     * @return AIResponseResult를 담은 Mono (성공/실패 여부와 원인 포함)
     */
    public Mono<AIResponseResult> generateResponseAsync(String prompt, AIRequestOptions options) {
        if (options == null) {
            options = AIRequestOptions.builder().build(); // 기본 옵션 사용
        }

        final AIRequestOptions finalOptions = options;

        return Mono.fromCallable(() -> generateResponseBlocking(prompt, finalOptions))
                .subscribeOn(Schedulers.boundedElastic())  // 별도 스레드 풀에서 실행
                .timeout(java.time.Duration.ofSeconds(finalOptions.getTimeoutSeconds()))
                .onErrorResume(e -> {
                    // 예외 타입별로 구분하여 처리
                    AIResponseResult.FailureType failureType;
                    String errorMessage;

                    if (e instanceof java.util.concurrent.TimeoutException) {
                        failureType = AIResponseResult.FailureType.TIMEOUT;
                        errorMessage = "GPT API 호출 타임아웃 (" + finalOptions.getTimeoutSeconds() + "초 초과)";
                        log.error("[GPT] {}", errorMessage);
                    } else if (e.getCause() instanceof SocketTimeoutException) {
                        failureType = AIResponseResult.FailureType.SOCKET_TIMEOUT;
                        errorMessage = "소켓 타임아웃: " + e.getCause().getMessage();
                        log.error("[GPT] {}", errorMessage);
                    } else if (e.getCause() instanceof IOException) {
                        failureType = AIResponseResult.FailureType.IO_ERROR;
                        errorMessage = "IO 오류: " + e.getCause().getMessage();
                        log.error("[GPT] {}", errorMessage);
                    } else if (e instanceof RuntimeException && e.getMessage() != null &&
                               e.getMessage().contains("OpenAiService")) {
                        failureType = AIResponseResult.FailureType.INITIALIZATION_ERROR;
                        errorMessage = "OpenAiService 초기화 실패: " + e.getMessage();
                        log.error("[GPT] {}", errorMessage);
                    } else {
                        failureType = AIResponseResult.FailureType.UNKNOWN_ERROR;
                        errorMessage = "알 수 없는 오류: " + e.getMessage();
                        log.error("[GPT] {}", errorMessage, e);
                    }

                    return Mono.just(AIResponseResult.failure(failureType, errorMessage));
                });
    }

    /**
     * GPT 비동기 응답 생성 (간편 버전, 기본 옵션 사용)
     */
    public Mono<AIResponseResult> generateResponseAsync(String prompt) {
        return generateResponseAsync(prompt, null);
    }

    /**
     * 동기 GPT 응답 생성 (내부적으로만 사용, 비동기 메서드에서 호출됨)
     * 예외를 던지지 않고 AIResponseResult로 반환
     */
    private AIResponseResult generateResponseBlocking(String prompt, AIRequestOptions options) {
        // API 키 확인
        if (apiKey == null || apiKey.isBlank()) {
            return AIResponseResult.failure(
                    AIResponseResult.FailureType.INITIALIZATION_ERROR,
                    "GPT API 키가 설정되지 않았습니다."
            );
        }

        // OpenAiService 초기화 시도
        OpenAiService service = null;
        try {
            service = getOpenAiService();
        } catch (ExceptionInInitializerError e) {
            return AIResponseResult.failure(
                    AIResponseResult.FailureType.INITIALIZATION_ERROR,
                    "OpenAiService 클래스 초기화 실패 (의존성 충돌): " + e.getMessage()
            );
        } catch (NoClassDefFoundError e) {
            return AIResponseResult.failure(
                    AIResponseResult.FailureType.INITIALIZATION_ERROR,
                    "OpenAiService 클래스를 찾을 수 없습니다: " + e.getMessage()
            );
        } catch (Throwable e) {
            return AIResponseResult.failure(
                    AIResponseResult.FailureType.INITIALIZATION_ERROR,
                    "OpenAiService 초기화 실패: " + e.getMessage()
            );
        }

        if (service == null) {
            return AIResponseResult.failure(
                    AIResponseResult.FailureType.INITIALIZATION_ERROR,
                    "OpenAiService가 null입니다."
            );
        }

        try {
            List<ChatMessage> messages = new ArrayList<>();

            // System prompt 추가
            messages.add(new ChatMessage(
                    ChatMessageRole.SYSTEM.value(),
                    options.getSystemPrompt()
            ));

            // 대화 기록 추가 (있는 경우)
            if (options.getConversationHistory() != null && !options.getConversationHistory().isEmpty()) {
                messages.addAll(options.getConversationHistory());
            }

            // User prompt 추가
            messages.add(new ChatMessage(
                    ChatMessageRole.USER.value(),
                    prompt
            ));

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(options.getTemperature())
                    .maxTokens(options.getMaxTokens())
                    .build();

            String content = service.createChatCompletion(completionRequest)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            if (content == null || content.isBlank()) {
                return AIResponseResult.failure(
                        AIResponseResult.FailureType.API_ERROR,
                        "GPT API가 빈 응답을 반환했습니다."
                );
            }

            return AIResponseResult.success(content);
        } catch (Exception e) {
            // OpenAiService의 createChatCompletion은 SocketTimeoutException이나 IOException을 직접 던지지 않음
            // 대신 일반 Exception으로 래핑되어 전달됨
            // 원인 예외를 확인하여 타입 구분
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                return AIResponseResult.failure(
                        AIResponseResult.FailureType.SOCKET_TIMEOUT,
                        "소켓 타임아웃: " + cause.getMessage()
                );
            } else if (cause instanceof IOException) {
                return AIResponseResult.failure(
                        AIResponseResult.FailureType.IO_ERROR,
                        "IO 오류: " + cause.getMessage()
                );
            } else {
                return AIResponseResult.failure(
                        AIResponseResult.FailureType.API_ERROR,
                        "GPT API 호출 실패: " + e.getMessage()
                );
            }
        }
    }
}

