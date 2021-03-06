/*
 * Copyright (C) 2018 Chatopera Inc, <https://www.chatopera.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chatopera.cc.concurrent.chatbot;

import com.chatopera.cc.app.basic.MainContext;
import com.chatopera.cc.app.handler.api.request.RestUtils;
import com.chatopera.cc.app.im.client.NettyClients;
import com.chatopera.cc.app.im.message.ChatMessage;
import com.chatopera.cc.app.model.Chatbot;
import com.chatopera.cc.concurrent.user.UserDataEvent;
import com.chatopera.cc.util.Constants;
import com.chatopera.chatbot.ChatbotAPIRuntimeException;
import com.lmax.disruptor.EventHandler;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;

@SuppressWarnings("rawtypes")
public class ChatbotEventHandler implements EventHandler<UserDataEvent> {
    private final static Logger logger = LoggerFactory.getLogger(ChatbotEventHandler.class);

    private void chat(final ChatbotEvent payload) throws MalformedURLException, ChatbotAPIRuntimeException {
        ChatMessage request = (ChatMessage) payload.getData();
        Chatbot c = payload.getChatbotRes()
                .findOne(request.getAiid());

        logger.info("[chatbot disruptor] chat request baseUrl {}, chatbotID {}, fromUserId {}, textMessage {}", c.getBaseUrl(), c.getChatbotID(), request.getUserid(), request.getMessage());
        // Get response from Conversational Engine.
        JSONObject result = c.getApi()
                .conversation(c.getChatbotID(), request.getUserid(), request.getMessage(), false);

        // parse response
        logger.info("[chatbot disruptor] chat response {}", result.toString());
        if(result.getInt(RestUtils.RESP_KEY_RC) == 0){
            // reply
            ChatMessage resp = new ChatMessage();
            resp.setCalltype(MainContext.CallTypeEnum.OUT.toString());
            resp.setOrgi(request.getOrgi());
            resp.setAiid(request.getAiid());
            resp.setMessage(result.getJSONObject("data").getString("string"));
            resp.setTouser(request.getUsername());
            resp.setMsgtype(request.getMsgtype());
            resp.setUserid(request.getUserid());
            resp.setChannel(request.getChannel());
            resp.setContextid(resp.getContextid());
            resp.setSessionid(resp.getSessionid());
            resp.setUsername(c.getName());
            NettyClients.getInstance().sendChatbotEventMessage(request.getUserid(), MainContext.MessageTypeEnum.MESSAGE.toString(), resp);
        } else {
            // TODO handle exceptions
        }
    }


    @Override
    public void onEvent(UserDataEvent event, long arg1, boolean arg2)
            throws Exception {
        ChatbotEvent payload = (ChatbotEvent) event.getEvent();
        switch (payload.getEventype()) {
            case Constants
                    .CHATBOT_EVENT_TYPE_CHAT:
                chat(payload);
                break;
            default:
                logger.warn("[chatbot disruptor] onEvent unknown.");
        }
    }

}
