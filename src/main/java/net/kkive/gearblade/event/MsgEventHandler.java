package net.kkive.gearblade.event;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.tekgator.queryminecraftserver.api.Protocol;
import com.tekgator.queryminecraftserver.api.QueryException;
import com.tekgator.queryminecraftserver.api.QueryStatus.Builder;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.console.plugin.jvm.JavaPluginScheduler;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsgEventHandler extends SimpleListenerHost {
    private static final Logger logger = LoggerFactory.getLogger(MsgEventHandler.class);
    final JavaPluginScheduler threadPoolTaskExecutor;
    Properties properties;
    private JSONObject outPutLog;
    private List<Integer> statusFlag = Collections.synchronizedList(new ArrayList<>(Collections.singletonList(0)));
    private List<Integer> voteFlag = Collections.synchronizedList(new ArrayList<>(Collections.singletonList(0)));
    private List<Integer> logFlag = Collections.synchronizedList(new ArrayList<>(Collections.singletonList(0)));
    private List<Integer> restartFlag = Collections.synchronizedList(new ArrayList<>(Collections.singletonList(0)));

    public MsgEventHandler(JavaPluginScheduler javaPluginScheduler, Properties properties) {
        this.properties = properties;
        this.threadPoolTaskExecutor = javaPluginScheduler;
    }

    public void handleException(CoroutineContext context, Throwable exception) {
        exception.printStackTrace();
    }

    @EventHandler
    public void onMessage(GroupMessageEvent event) {
        threadPoolTaskExecutor.async(() -> {
            msgProcess(event);
        });
    }

    private void timer(Long delayMillis, List<Integer> flag) {
        flag.set(0, 1);
        threadPoolTaskExecutor.delayed(delayMillis, () -> {
            flag.set(0, 0);
        });
    }

    private void msgProcess(GroupMessageEvent event) {
        String groupId = String.valueOf(event.getGroup().getId());
        String permitGroup = properties.getProperty("PermitGroup");
        String[] permitGroupList = permitGroup.split(",");
        boolean havePermitGroup = false;

        for (String str : permitGroupList) {
            if (str.contains(groupId)) {
                havePermitGroup = true;
                break;
            }
        }
        if (!havePermitGroup) {
            return;
        }

        SingleMessage singleMessage = event.getMessage().get(PlainText.Key);
        if (singleMessage != null) {
            String msg = singleMessage.contentToString();
            String[] CMD = msg.split("\\s+", 3);
            switch (CMD[0]) {
                case "/status":
                    if (statusFlag.get(0) != 0) {
                        event.getSubject().sendMessage("命令冷却中稍后再试。");
                        return;
                    }
                    timer(60000L, statusFlag);
                    MCSStatusSend(event);
                    break;
                case "/vote":
                    if (voteFlag.get(0) != 0) {
                        event.getSubject().sendMessage("命令冷却中稍后再试。");
                        return;
                    }
                    timer(10000L, voteFlag);
                    if (CMD.length < 3) {
                        event.getSubject().sendMessage("命令格式不完整。");
                        return;
                    }
                    voteCMDProcessAndSend(event, CMD[1], CMD[2]);
                    break;
                case "/log":
                    if (logFlag.get(0) != 0) {
                        event.getSubject().sendMessage("命令冷却中稍后再试。");
                        return;
                    }

                    timer(5000L, logFlag);
                    if (CMD.length < 2) {
                        event.getSubject().sendMessage("命令格式不完整。");
                        return;
                    }
                    getOutPutLog(event, CMD[1]);
                default:
                    return;

            }
        }


    }

    private List<String> selectServer(GroupMessageEvent event, String server) {
        String instanceUUID = properties.getProperty("InstanceUUID");
        String[] temp1 = instanceUUID.split(";");
        String[] temp2 = server.split("\\.");
        if (temp2.length != 2) {
            event.getSubject().sendMessage("命令格式错误请重试。");
            return null;
        } else {
            int[] serverSelect = new int[2];
            for (int i = 0; i < 2; ++i) {
                if (temp2[i] == null || temp2[i].equals("")) {
                    event.getSubject().sendMessage("命令格式错误请重试。");
                    return null;
                }
                try {
                    serverSelect[i] = Integer.parseInt(temp2[i]) - 1;
                } catch (Exception var10) {
                    event.getSubject().sendMessage("命令格式错误请重试。");
                    return null;
                }
                if (serverSelect[i] < 0) {
                    event.getSubject().sendMessage("命令格式错误请重试。");
                    return null;
                }
            }

            if (serverSelect[0] >= temp1.length) {
                event.getSubject().sendMessage("超过节点数请重试。");
                return null;
            } else {
                temp1 = temp1[serverSelect[0]].split(":");
                String instanceProtectUUID = temp1[0];
                String[] temp3 = temp1[1].split(",");
                if (serverSelect[1] >= temp3.length) {
                    event.getSubject().sendMessage("超过实例数请重试。");
                    return null;
                } else {
                    instanceUUID = temp3[serverSelect[1]];
                    List<String> resultSet = new ArrayList<>();
                    resultSet.add(instanceUUID);
                    resultSet.add(instanceProtectUUID);
                    return resultSet;
                }
            }
        }
    }

    private JSONObject getInstanceJSONObject(GroupMessageEvent event, String instanceUUID, String instanceProtectUUID) {
        String apiKey = properties.getProperty("ApiKey");
        String urlOrData = "localhost:23333/api/protected_instance/outputlog?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
        HttpResponse response = HttpRequest.get(urlOrData).execute();
        JSONObject httpResponseJSONObjectOrInstanceProtectJSONObject = JSONObject.parseObject(response.body());
        response.close();
        if (httpResponseJSONObjectOrInstanceProtectJSONObject.getInteger("status") != 200) {
            event.getSubject().sendMessage("请求失败请稍后再试。");
            return null;
        }
        try {
            urlOrData = httpResponseJSONObjectOrInstanceProtectJSONObject.getString("data");
        } catch (NullPointerException nullPointerException) {
            event.getSubject().sendMessage("无数据请稍后再试。");
            return null;
        }
        int beginIndex = urlOrData.length();
        beginIndex = beginIndex / 2 + beginIndex / 4 + beginIndex / 8;
        if (beginIndex >= urlOrData.length()) {
            beginIndex = urlOrData.length() / 2;
        }
        urlOrData = urlOrData.substring(beginIndex);
        JSONObject instanceJSONObject = new JSONObject();
//        不包含时间戳
//        List<String> chatList = new ArrayList<>();
//        Pattern p = Pattern.compile("(?<=]: ((?=<)|(?=\\[S))).*?(?=\\r\\n)");
//        Matcher matcher = p.matcher(urlOrData);
//        while (matcher.find()) {
//            chatList.add(matcher.group());
//        }
//        if (chatList.size() == 0) {
//            chatList.add("没有找到消息。");
//        } else {
//            if (chatList.size() > 20) {
//                chatList = chatList.subList(chatList.size() - 20, chatList.size());
//            }
//        }
//        instanceJSONObject.put("data", chatList);
//        instanceJSONObject.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        List<String> chatList = new ArrayList<>();
        List<String> timeStamp = new ArrayList<>();
        Pattern p = Pattern.compile("(\\[([0-1]?[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])).*?(?=\\r\\n)");
        Matcher matcher = p.matcher(urlOrData);
        while (matcher.find()) {
            String matcherGroup = matcher.group() + "\r\n";
            p = Pattern.compile("(?<=]: ((?=<)|(?=\\[S))).*?(?=\\r\\n)");
            Matcher timeOrSentenceMatcher = p.matcher(matcherGroup);
            if (timeOrSentenceMatcher.find()) {
                chatList.add(timeOrSentenceMatcher.group());
                p = Pattern.compile("([0-1]?[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])");
                timeOrSentenceMatcher = p.matcher(matcherGroup);
                if (timeOrSentenceMatcher.find()) {
                    timeStamp.add(timeOrSentenceMatcher.group());
                } else {
                    timeStamp.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }

            }

        }
        if (chatList.size() == 1) {
            timeStamp.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } else if (chatList.size() == 0) {
            chatList.add("没有找到消息。");
            p = Pattern.compile("([0-1]?[0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])");
            matcher = p.matcher(urlOrData);
            if (matcher.find()) {
                timeStamp.add(matcher.group());
            } else {
                timeStamp.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            timeStamp.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        } else {
            if (chatList.size() > 20) {
                chatList = chatList.subList(chatList.size() - 20, chatList.size());
                timeStamp = timeStamp.subList(timeStamp.size() - 20, timeStamp.size());
            }
        }

        instanceJSONObject.put("data", chatList);
        instanceJSONObject.put("dataTime", timeStamp);
        instanceJSONObject.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return instanceJSONObject;
    }

    private void getOutPutLog(GroupMessageEvent event, String server) {
        List<String> temp = selectServer(event, server);
        if (temp == null || temp.isEmpty()) {
            return;
        }
        String instanceUUID = temp.get(0);
        String instanceProtectUUID = temp.get(1);
        List<String> chatList;
        List<String> timeStamp;
        String apiKey = properties.getProperty("ApiKey");
        String url = "localhost:23333/api/instance?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
        HttpResponse response = HttpRequest.get(url).execute();
        JSONObject jsonObject = JSONObject.parseObject(response.body());
        response.close();

        if (!jsonObject.getString("status").equals("200") || !jsonObject.getJSONObject("data").getString("status").equals("3")) {
            event.getSubject().sendMessage("实例处于关闭或者链接失败，请稍后再试。");
            return;
        }
        jsonObject = null;

        if (outPutLog == null || outPutLog.isEmpty()) {
            JSONObject instanceJSONObject = getInstanceJSONObject(event, instanceUUID, instanceProtectUUID);
            chatList = instanceJSONObject.getJSONArray("data").toJavaList(String.class);
            timeStamp = instanceJSONObject.getJSONArray("dataTime").toJavaList(String.class);
            JSONObject instanceProtectJSONObject = new JSONObject();
            instanceProtectJSONObject.put(instanceUUID, instanceJSONObject);
            instanceJSONObject = null;
            outPutLog = new JSONObject();
            outPutLog.put(instanceProtectUUID, instanceProtectJSONObject);
        } else {
            JSONObject outPutLogCacheOrInstanceProtectJSONObject = outPutLog.getJSONObject(instanceProtectUUID);
            if (outPutLogCacheOrInstanceProtectJSONObject == null || outPutLogCacheOrInstanceProtectJSONObject.isEmpty()) {
                JSONObject instanceJSONObject = getInstanceJSONObject(event, instanceUUID, instanceProtectUUID);
                chatList = instanceJSONObject.getJSONArray("data").toJavaList(String.class);
                timeStamp = instanceJSONObject.getJSONArray("dataTime").toJavaList(String.class);
                outPutLogCacheOrInstanceProtectJSONObject = new JSONObject();
                outPutLogCacheOrInstanceProtectJSONObject.put(instanceUUID, instanceJSONObject);
                instanceJSONObject = null;
                outPutLog.put(instanceProtectUUID, outPutLogCacheOrInstanceProtectJSONObject);
            } else {
                outPutLogCacheOrInstanceProtectJSONObject = outPutLog.getJSONObject(instanceProtectUUID).getJSONObject(instanceUUID);
                if (outPutLogCacheOrInstanceProtectJSONObject == null || outPutLogCacheOrInstanceProtectJSONObject.isEmpty()
                        || ChronoUnit.MINUTES.between(LocalDateTime.parse(outPutLogCacheOrInstanceProtectJSONObject.getString("date"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), LocalDateTime.now()) >= 2L) {
                    JSONObject instanceJSONObject = getInstanceJSONObject(event, instanceUUID, instanceProtectUUID);
                    chatList = instanceJSONObject.getJSONArray("data").toJavaList(String.class);
                    timeStamp = instanceJSONObject.getJSONArray("dataTime").toJavaList(String.class);
                    outPutLogCacheOrInstanceProtectJSONObject = outPutLog.getJSONObject(instanceProtectUUID);
                    outPutLogCacheOrInstanceProtectJSONObject.put(instanceUUID, instanceJSONObject);
                    instanceJSONObject = null;
                    outPutLog.put(instanceProtectUUID, outPutLogCacheOrInstanceProtectJSONObject);
                } else {
                    chatList = outPutLog.getJSONObject(instanceProtectUUID).getJSONObject(instanceUUID).getJSONArray("data").toJavaList(String.class);
                    timeStamp = outPutLog.getJSONObject(instanceProtectUUID).getJSONObject(instanceUUID).getJSONArray("dataTime").toJavaList(String.class);
                }
            }
        }

        event.getSubject().sendMessage("[" + timeStamp.get(0) + "]\n" + String.join("\n", chatList) + "\n" +
                "[" + timeStamp.get(timeStamp.size() - 1) + "]");

    }

    private void voteCMDProcessAndSend(GroupMessageEvent event, String server, String cmd) {
        List<String> temp = selectServer(event, server);
        if (temp == null || temp.isEmpty()) {
            return;
        }
        String instanceUUID = temp.get(0);
        String instanceProtectUUID = temp.get(1);
        String apiKey = properties.getProperty("ApiKey");
        String[] cmdList = cmd.split("\\s+", 2);
        switch (cmdList[0]) {
            case "say":
                if (cmdList.length < 2 || cmdList[1] == null || cmdList[1].equals("")) {
                    event.getSubject().sendMessage("命令格式错误。");
                    return;
                }
                saySomethingsToServer(event, instanceProtectUUID, instanceUUID, apiKey, cmdList[1]);
                break;
            case "restart":
                restartServer(event, instanceProtectUUID, instanceUUID, apiKey);
                break;
            default:
                event.getSubject().sendMessage("没有该命令。");
        }

    }

    private void saySomethingsToServer(GroupMessageEvent event, String instanceProtectUUID, String instanceUUID, String apiKey, String msg) {
        String url = "localhost:23333/api/protected_instance/command?uuid=" + instanceUUID + "&remote_uuid=" +
                instanceProtectUUID + "&apikey=" + apiKey + "&command=say 来自" + event.getSenderName() + "的消息:" + msg;
        HttpResponse response = HttpRequest.get(url).execute();
        JSONObject jsonObject = JSONObject.parseObject(response.body());
        response.close();
        if (jsonObject.getString("status").equals("200")) {
            event.getSubject().sendMessage("发送成功。");
        } else {
            event.getSubject().sendMessage("发送失败。");
        }

    }

    private void restartServer(GroupMessageEvent event, String instanceProtectUUID, String instanceUUID, String apiKey) {
        LocalTime startTime = LocalTime.of(6, 0);
        LocalTime endTime = LocalTime.of(10, 0);
        LocalTime nowTime = LocalTime.now();

        if (!nowTime.isBefore(startTime) && !nowTime.isAfter(endTime)) {
            String url = "localhost:23333/api/instance?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
            HttpResponse response = HttpRequest.get(url).execute();
            JSONObject jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getString("status").equals("200") || jsonObject.getJSONObject("data").getString("status").equals("0")) {
                event.getSubject().sendMessage("实例处于关闭或者链接失败，请稍后再试。");
                return;
            } else {
                if (!jsonObject.getJSONObject("data").getJSONObject("info").getString("currentPlayers").equals("-1")) {
                    event.getSubject().sendMessage("实例处于正常启动状态不能重启。");
                    return;
                }
            }
            event.getSubject().sendMessage("准备重启。");
            url = "localhost:23333/api/protected_instance/restart?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
            response = HttpRequest.get(url).execute();
            jsonObject = JSONObject.parseObject(response.body());
            response.close();
            if (jsonObject.getInteger("status") == 200) {
                if (restartFlag.get(0) != 0) {
                    event.getSubject().sendMessage("实例或其他实例处于重启中请稍后再试。");
                    return;
                }
                timer(45000L, restartFlag);
                try {
                    Thread.sleep(45000);
                } catch (InterruptedException ignored) {

                }
                url = "localhost:23333/api/instance?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
                response = HttpRequest.get(url).execute();
                jsonObject = JSONObject.parseObject(response.body());
                response.close();

                if (jsonObject.getInteger("status") == 200) {
                    if (jsonObject.getJSONObject("data").getInteger("status") == -1) {
                        event.getSubject().sendMessage("重启状态查询失败请重试。");
                        return;
                    } else {
                        if (jsonObject.getJSONObject("data").getJSONObject("info") != null &&
                                !jsonObject.getJSONObject("data").getJSONObject("info").isEmpty() &&
                                jsonObject.getJSONObject("data").getJSONObject("info").getInteger("currentPlayers") != -1) {
                            event.getSubject().sendMessage("实例处于正常状态重启取消。");
                            return;
                        }
                    }


                } else {
                    event.getSubject().sendMessage("重启状态查询失败请重试。");
                }

                url = "localhost:23333/api/protected_instance/kill?uuid=" + instanceUUID + "&remote_uuid=" + instanceProtectUUID + "&apikey=" + apiKey;
                response = HttpRequest.get(url).execute();
                jsonObject = JSONObject.parseObject(response.body());
                response.close();
                if (jsonObject.getString("status").equals("200")) {
                    event.getSubject().sendMessage("发送重启成功。");
                } else {
                    event.getSubject().sendMessage("重启失败请稍后再试。");
                }
            } else {
                event.getSubject().sendMessage("重启失败请稍后再试。");
            }

        } else {
            event.getSubject().sendMessage("重启命令只能在6-10点使用。");
        }
    }

    private void MCSStatusSend(GroupMessageEvent event) {
        ArrayList<String> InstanceStatusList = new ArrayList<>();
        String QueryIP = properties.getProperty("QueryIP");
        String[] QueryIPList = QueryIP.split(",");
        for (int i = 1; i < QueryIPList.length + 1; i++) {
            String[] addressAndPort = QueryIPList[i - 1].split(":");
            JSONObject serverStatus = new JSONObject();
            try {
                serverStatus = JSONObject.parseObject(new Builder(addressAndPort[0])
                        .setPort(Integer.parseInt(addressAndPort[1]))
                        .setProtocol(Protocol.TCP)
                        .build()
                        .getStatus()
                        .toJson());
            } catch (QueryException e) {
                e.printStackTrace();
                InstanceStatusList.add(i + "服查询失败请稍后再试。");
            }
            JSONObject playersJSON = serverStatus.getJSONObject("players");
            if (playersJSON.getString("online").equals("0")) {
                InstanceStatusList.add("现在" + i + "服共有" + 0 + "人。");
                continue;
            }
            JSONArray jsonArray = playersJSON.getJSONArray("sample");
            InstanceStatusList.add("现在" + i + "服共有" + playersJSON.getString("online") + "人，分别是");
            for (int j = 0; j < jsonArray.size(); j++) {
                InstanceStatusList.add("- " + jsonArray.getJSONObject(j).getString("name"));
            }

        }
        event.getSubject().sendMessage(String.join("\n", InstanceStatusList));
    }
}
