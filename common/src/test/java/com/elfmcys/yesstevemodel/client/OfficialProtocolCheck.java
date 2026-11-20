package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.mixin.ConnectionAccessor;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.ModelSyncWireFormat;
import net.everla.reimaginedysm.security.YSMByteBuf;
import com.elfmcys.yesstevemodel.network.message.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.InteractionHand;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Actual release encoder bytecode is executed in isolation: no Forge handlers or official native code. */
public final class OfficialProtocolCheck {
    private static final String PACKAGE = "com/elfmcys/yesstevemodel/";
    private static final String BUFFER = "net/minecraft/network/FriendlyByteBuf";
    private record Case(int id, String officialClass, Object[] fields, Consumer<FriendlyByteBuf> encoder) {}
    private static byte[] bytes(Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { encoder.accept(buf); byte[] bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); return bytes; }
        finally { buf.release(); }
    }
    private static final class TestConnection extends Connection implements ConnectionAccessor {
        private final EmbeddedChannel testChannel = new EmbeddedChannel();
        TestConnection() { super(PacketFlow.CLIENTBOUND); }
        public Channel ysm$getChannel() { return testChannel; }
    }
    public static void main(String[] args) throws Exception {
        FloatArrayList floats = new FloatArrayList(new float[]{1.25f, -2.5f});
        S2CSyncPlayerStatePacket state = new S2CSyncPlayerStatePacket(300);
        state.flags=(short)4090; state.isFlying=true; state.experienceLevel=7; state.foodLevel=20;
        state.health=18; state.maxHealth=20; state.strafeInput=-127; state.verticalInput=0; state.forwardInput=127;
        state.shieldBlockCooldown=true; state.modelSwitchId="test/model";
        List<Case> cases = List.of(
            new Case(1,"O0oo00oo00OoOooO000oO00O",new Object[]{ByteBuffer.wrap(new byte[]{3,1,4})}, b -> S2CModelSyncPayload.encode(new S2CModelSyncPayload(ByteBuffer.wrap(new byte[]{3,1,4})),b)),
            new Case(2,"oo00oOo0OOO0OoOOo0O0oO0O",new Object[]{ByteBuffer.wrap(new byte[]{2,7,1})}, b -> C2SModelSyncPayload.encode(new C2SModelSyncPayload(ByteBuffer.wrap(new byte[]{2,7,1})),b)),
            new Case(3,"o0oo0O0o0oO0O0o0OoOOOoOO",new Object[]{new int[]{42,300},"test"}, b -> S2CExecuteMolangPacket.encode(new S2CExecuteMolangPacket(new int[]{42,300},"test"),b)),
            new Case(5,"O0Oo0oooOoOOO0oOoOOoOooo",new Object[]{"test/model","blue"}, b -> C2SRequestSwitchModelPacket.encode(new C2SRequestSwitchModelPacket("test/model","blue"),b)),
            new Case(6,"OoOOOoOOOOooo00o0OOoooOo",new Object[]{Set.of("test/model")}, b -> S2CSyncAuthModelsPacket.encode(new S2CSyncAuthModelsPacket(Set.of("test/model")),b)),
            new Case(7,"OO00OOoo0ooO0o000O0oO0oO",new Object[]{2,"main",-1}, b -> C2SPlayAnimationPacket.encode(new C2SPlayAnimationPacket(2,"main",-1),b)),
            new Case(8,"OoO0O0Oo0oooo0O0Oo000o0O",new Object[]{Set.of("test/model")}, b -> S2CSyncStarModelsPacket.encode(new S2CSyncStarModelsPacket(Set.of("test/model")),b)),
            new Case(9,"oO0o0o0ooOO0OO0O0oO0OOo0",new Object[]{"test/model",true}, b -> C2SSetStarModelPacket.encode(C2SSetStarModelPacket.add("test/model"),b)),
            new Case(17,"oOooOooO0Oo0oo0o0o00O0Oo",new Object[]{"wave",300}, b -> C2SRequestExecuteMolangPacket.encode(new C2SRequestExecuteMolangPacket("wave",300),b)),
            new Case(18,"O0oOoOOo00O0OoOoOoOO0oo0",new Object[]{floats}, b -> C2SSyncAnimationExpressionPacket.encode(new C2SSyncAnimationExpressionPacket(floats),b)),
            new Case(19,"oooo0oooO0O00000ooOO0OoO",new Object[]{300,floats}, b -> S2CSyncAnimationExpressionPacket.encode(new S2CSyncAnimationExpressionPacket(300,floats),b)),
            new Case(21,"O00OO0Oo0O0Oo0O0Ooo0o00O",new Object[]{300,(short)4090,true,null,7,20,18,20,(byte)-127,(byte)0,(byte)127,true,"test/model",0,null,null}, b -> S2CSyncPlayerStatePacket.encode(state,b)),
            new Case(23,"oOO0O00OOoO00000OoOoOOo0",new Object[]{InteractionHand.OFF_HAND}, b -> C2SSwingArmPacket.encode(new C2SSwingArmPacket(InteractionHand.OFF_HAND),b)),
            new Case(51,"oO000OOo0o000oOoo0O000Oo",new Object[]{"2.6.0"}, b -> b.writeUtf("2.6.0")),
            new Case(52,"o0O0oo00OO00oO0O0o0o0o00",new Object[]{"2.6.0"}, b -> C2SVersionCheckPacket.encode(new C2SVersionCheckPacket(),b))
        );
        TestConnection connection = new TestConnection();
        try {
            NetworkHandler.setChannelVersion(connection, NetworkHandler.OFFICIAL_VERSION);
            NetworkHandler.selectClientProtocol(connection);
            assert NetworkHandler.isOfficialServer();
            Path fixtures = Path.of(System.getProperty("ysm.protocolFixtures"));
            String officialJar = System.getProperty("ysm.officialJar");
            boolean generate = Boolean.getBoolean("ysm.writeProtocolFixtures");
            for (Case sample : cases) {
                byte[] actual = bytes(sample.encoder);
                Path fixture = fixtures.resolve(sample.id + ".hex");
                if (officialJar != null) {
                    byte[] expected = releaseBytes(Path.of(officialJar), sample);
                    assert Arrays.equals(expected,actual) : "Official encoder mismatch: packet " + sample.id;
                    if (generate) { Files.createDirectories(fixtures); Files.writeString(fixture,HexFormat.of().formatHex(expected)+"\n"); }
                }
                assert Arrays.equals(HexFormat.of().parseHex(Files.readString(fixture).trim()), actual) : "Fixture mismatch: packet " + sample.id;
            }
            Set<Integer> ids = Set.of(1,2,3,4,5,6,7,8,9,15,16,17,18,19,21,22,23,51,52);
            for (int id=0;id<256;id++) assert NetworkHandler.isOfficialPacket(id)==ids.contains(id) : "Wrong official packet ID: " + id;
            FriendlyByteBuf greeting = new FriendlyByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex("05322e362e30")));
            try { S2CVersionCheckPacket.decode(greeting); assert !greeting.isReadable(); } finally { greeting.release(); }
            NetworkHandler.resetClientHandshake();
            assert !NetworkHandler.isOfficialServer() : "Protocol leaked across disconnect";
            assert !NetworkHandler.serverSupportsModelSyncFragments();
            byte[] modern = bytes(b -> C2SVersionCheckPacket.encode(new C2SVersionCheckPacket(),b));
            assert modern.length>6 : "Modern capabilities lost";
            assert modern[0]==5 && modern[3]=='7' : "Modern version changed";
        } finally { connection.testChannel.finishAndReleaseAll(); NetworkHandler.resetClientHandshake(); }
        // Unencrypted catalog-entry layout from the local reconstructed 2.6.5 reference.
        // This fixture is separate from the release encoder fixtures; it is not a native packet capture.
        byte[] entryBytes=HexFormat.of().parseHex("010203612f6201012000");
        try (YSMByteBuf entryBuf=new YSMByteBuf(Unpooled.wrappedBuffer(entryBytes))) {
            var entry=ModelSyncWireFormat.readEntry(entryBuf,true);
            assert entry.hash1()==1 && entry.hash2()==2 && entry.id().equals("a/b") && entry.auth() && entry.version()==32;
            assert entry.metadata()==null && entryBuf.readVarInt()==0 : "Official parser consumed the following pack count";
            try (YSMByteBuf request=new YSMByteBuf(Unpooled.buffer())) {
                ModelSyncWireFormat.writeOfficialRequests(request,List.of(entry));
                assert HexFormat.of().formatHex(request.toArray()).equals("010102") : "Extensions leaked into official request";
            }
        }
        ModelSyncWireFormat.validateChunk(10,0,5,5,0);
        ModelSyncWireFormat.validateChunk(10,5,5,5,5);
        for (int[] invalid : new int[][]{{10,0,5,5,5},{10,7,5,5,5},{10,5,5,4,5},{0,0,0,0,0},{10,-1,5,5,0}}) {
            boolean rejected=false;
            try { ModelSyncWireFormat.validateChunk(invalid[0],invalid[1],invalid[2],invalid[3],invalid[4]); }
            catch (IllegalArgumentException expected) { rejected=true; }
            assert rejected : "Invalid chunk accepted";
        }
        System.out.println("PASS: 15 release encoder fixtures, packet allowlist, session reset, legacy catalog/request layout and chunk bounds");
    }

    private static byte[] releaseBytes(Path jar, Case sample) throws Exception {
        ClassNode source = new ClassNode();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            new ClassReader(zip.getInputStream(zip.getEntry(PACKAGE+sample.officialClass+".class"))).accept(source,ClassReader.SKIP_DEBUG);
        }
        String descriptor = "(L"+source.name+";L"+BUFFER+";)V";
        MethodNode encoder = source.methods.stream().filter(m -> m.desc.equals(descriptor)).findFirst().orElseThrow();
        Map<String,String> names = Map.of("m_130070_","writeUtf","m_130130_","writeVarInt","m_130089_","writeVarIntArray","m_130068_","writeEnum");
        for (AbstractInsnNode instruction : encoder.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(BUFFER)) call.name = names.getOrDefault(call.name,call.name);
        }
        encoder.name="encode";
        encoder.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;
        ClassWriter writer = new ClassWriter(0);
        writer.visit(source.version,Opcodes.ACC_PUBLIC,source.name,null,"java/lang/Object",null);
        List<FieldNode> fields = source.fields.stream().filter(f -> (f.access&Opcodes.ACC_STATIC)==0).toList();
        for (FieldNode field : fields) writer.visitField(Opcodes.ACC_PUBLIC,field.name,field.desc,null,null).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(1,1);init.visitEnd();
        encoder.accept(writer);writer.visitEnd();
        byte[] bytecode=writer.toByteArray();
        Class<?> type = new ClassLoader(OfficialProtocolCheck.class.getClassLoader()) {
            Class<?> define() { return defineClass(null,bytecode,0,bytecode.length); }
        }.define();
        Object packet=type.getConstructor().newInstance();
        assert fields.size()==sample.fields.length : "Release fields changed: " + sample.id;
        for(int i=0;i<fields.size();i++) type.getField(fields.get(i).name).set(packet,sample.fields[i]);
        var method=type.getMethod("encode",type,FriendlyByteBuf.class);
        return bytes(buf -> { try { method.invoke(null,packet,buf); } catch(Exception e) { throw new RuntimeException(e); } });
    }
}
