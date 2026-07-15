package dev.placeholder.llmd.ipc;

import dev.placeholder.llmd.ipc.ILlmdChatCallback;

interface ILlmdService {
    String health();
    String listModels();
    String chatCompletion(String requestJson);
    void chatCompletionAsync(String requestJson, ILlmdChatCallback callback);
}
