package dev.placeholder.llmd.ipc;

import dev.placeholder.llmd.ipc.ILlmdChatCallback;

interface ILlmdService {
    void healthAsync(ILlmdChatCallback callback);
    void listModelsAsync(ILlmdChatCallback callback);
    void chatCompletionAsync(String requestJson, ILlmdChatCallback callback);
}
