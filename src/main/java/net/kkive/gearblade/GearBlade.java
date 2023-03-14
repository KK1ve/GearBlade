package net.kkive.gearblade;


import net.kkive.gearblade.event.MsgEventHandler;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.event.GlobalEventChannel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class GearBlade extends JavaPlugin {
    public static final GearBlade INSTANCE = new GearBlade();
    private final Path path = Paths.get(this.resolveConfigPath("").toString(), "app.properties");
    private Properties properties;


    private GearBlade() {
        super((new JvmPluginDescriptionBuilder("net.kkive.gearblade", "0.3.0")).name("GearBlade").author("KKive").build());
        if (!this.path.toFile().isFile()) {
            try {
                this.getLogger().info("Generate the configuration item ");
                File f = this.path.toFile();
                f.createNewFile();
                this.writeDefaultProperties(f);
            } catch (IOException var3) {
                throw new RuntimeException(var3);
            }
        }

        this.properties = new Properties();

        try {
            this.properties.load(new InputStreamReader(new FileInputStream(this.path.toString()), StandardCharsets.UTF_8));
        } catch (IOException var2) {
            throw new RuntimeException(var2);
        }
    }

    private void writeDefaultProperties(File f) throws FileNotFoundException {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("#需要查询的服务器ip eg 127.0.0.1:24445,127.0.0.1:24446\n").append("QueryIP=127.0.0.1:24445,127.0.0.1:24446\n");
        stringBuffer.append("#可使用的群号，半角逗号分隔。 eg 12345, 15154\n").append("PermitGroup=123,123,123\n");
        stringBuffer.append("#apiKey。 eg 123\n").append("ApiKey=123\n");
        stringBuffer.append("#需要进行操作的守护进程及其实例UUID，守护进程UUID在前，实例UUID在后 eg 123:123,123;\n").append("InstanceUUID=123:123,123;\n");
        PrintStream p = new PrintStream(f);
        p.println(stringBuffer);
        p.close();
    }

    private void printConfig() {
        String permitGroup = this.properties.getProperty("PermitGroup");
        String queryIP = this.properties.getProperty("QueryIP");
        String instanceUUID = this.properties.getProperty("InstanceUUID");
        String apiKey = this.properties.getProperty("ApiKey");
        this.getLogger().info("loader PermitGroup " + permitGroup);
        this.getLogger().info("loader QueryURL " + queryIP);
        this.getLogger().info("loader InstanceUUID " + instanceUUID);
        this.getLogger().info("loader ApiKey " + apiKey);
    }

    public void onEnable() {
        this.printConfig();
        GlobalEventChannel.INSTANCE.registerListenerHost(new MsgEventHandler(this.getScheduler(), this.properties));
    }
}