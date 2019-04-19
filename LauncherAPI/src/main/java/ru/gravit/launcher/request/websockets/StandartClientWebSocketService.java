package ru.gravit.launcher.request.websockets;

import com.google.gson.GsonBuilder;
import ru.gravit.launcher.events.request.ErrorRequestEvent;
import ru.gravit.launcher.request.RequestException;
import ru.gravit.launcher.request.ResultInterface;
import ru.gravit.utils.helper.JVMHelper;
import ru.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.util.concurrent.*;

public class StandartClientWebSocketService extends ClientWebSocketService {
    public WaitEventHandler waitEventHandler = new WaitEventHandler();
    public StandartClientWebSocketService(GsonBuilder gsonBuilder, String address, int i) {
        super(gsonBuilder, address, i);
    }
    public class RequestFuture implements Future
    {
        public final WaitEventHandler.ResultEvent event;
        public boolean isCanceled = false;

        public RequestFuture(RequestInterface request) throws IOException {
            event = new WaitEventHandler.ResultEvent();
            event.type = request.getType();
            waitEventHandler.requests.add(event);
            sendObject(request);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            waitEventHandler.requests.remove(event);
            isCanceled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return isCanceled;
        }

        @Override
        public boolean isDone() {
            return event.ready;
        }

        @Override
        public ResultInterface get() throws InterruptedException, ExecutionException {
            if(isCanceled) return null;
            while (!event.ready) {
                synchronized (event) {
                    event.wait();
                }
            }
            ResultInterface result = event.result;
            waitEventHandler.requests.remove(event);
            if (event.result.getType().equals("error")) {
                ErrorRequestEvent errorRequestEvent = (ErrorRequestEvent) event.result;
                throw new ExecutionException(new RequestException(errorRequestEvent.error));
            }
            return result;
        }

        @Override
        public ResultInterface get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
            if(isCanceled) return null;
            while (!event.ready) {
                synchronized (event) {
                    event.wait(timeout);
                }
            }
            ResultInterface result = event.result;
            waitEventHandler.requests.remove(event);
            if (event.result.getType().equals("error")) {
                ErrorRequestEvent errorRequestEvent = (ErrorRequestEvent) event.result;
                throw new ExecutionException(new RequestException(errorRequestEvent.error));
            }
            return result;
        }
    }
    public ResultInterface sendRequest(RequestInterface request) throws IOException, InterruptedException {
        RequestFuture future = new RequestFuture(request);
        ResultInterface result;
        try {
            result = future.get();
        } catch (ExecutionException e) {
            throw (RequestException) e.getCause();
        }
        return result;
    }
    public RequestFuture asyncSendRequest(RequestInterface request) throws IOException {
        return new RequestFuture(request);
    }

    public static StandartClientWebSocketService initWebSockets(String address, boolean async) {
        StandartClientWebSocketService service = new StandartClientWebSocketService(new GsonBuilder(), address, 5000);
        service.registerResults();
        service.registerRequests();
        service.registerHandler(service.waitEventHandler);
        if(!async)
        {
            try {
                if (!service.connectBlocking()) LogHelper.error("Error connecting");
                LogHelper.debug("Connect to %s", address);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        else
        {
            service.connect();
        }
        JVMHelper.RUNTIME.addShutdownHook(new Thread(() -> {
            try {
                if(service.isOpen())
                    service.closeBlocking();
            } catch (InterruptedException e) {
                LogHelper.error(e);
            }
        }));
        return service;
    }
}
