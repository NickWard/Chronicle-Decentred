package net.openhft.chronicle.decentred.remote.rpc;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.decentred.api.SystemMessageListener;
import net.openhft.chronicle.decentred.dto.VanillaSignedMessage;
import net.openhft.chronicle.decentred.remote.net.TCPClientListener;
import net.openhft.chronicle.decentred.remote.net.TCPConnection;
import net.openhft.chronicle.decentred.remote.net.VanillaTCPClient;
import net.openhft.chronicle.decentred.util.DtoParser;
import net.openhft.chronicle.decentred.util.DtoRegistry;
import net.openhft.chronicle.decentred.util.LongObjMap;
import net.openhft.chronicle.wire.AbstractMethodWriterInvocationHandler;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static net.openhft.chronicle.decentred.util.DtoRegistry.MASK_16;

public class RPCClient<UP, DOWN> implements Closeable, TCPConnection {
    private final VanillaTCPClient tcpClient;
    private final UP proxy;
    private final DOWN listener;
    private final BytesStore secretKey;
    private final DtoRegistry<DOWN> registry;
    private final DtoParser<DOWN> parser;
    private final LongObjMap<BytesStore> addressToPublicKey =
            LongObjMap.withExpectedSize(BytesStore.class, 16);
    private boolean internal = false;

    public RPCClient(String name,
                     String socketHost,
                     int socketPort,
                     BytesStore secretKey,
                     Class<UP> upClass,
                     DtoRegistry<DOWN> registry,
                     DOWN listener) {
        this(name, Arrays.asList(new InetSocketAddress(socketHost, socketPort)), secretKey, upClass, registry, listener);
    }

    public RPCClient(String name,
                     List<InetSocketAddress> socketAddresses,
                     BytesStore secretKey,
                     Class<UP> upClass,
                     DtoRegistry<DOWN> registry,
                     DOWN listener) {
        this.secretKey = secretKey;
        this.parser = registry.get();
        this.registry = registry;
        InvocationHandler handler = new AbstractMethodWriterInvocationHandler() {
            @Override
            protected void handleInvoke(Method method, Object[] args) {
                assert args.length == 1;
                VanillaSignedMessage vsm = (VanillaSignedMessage) args[0];
                write(vsm);
            }
        };
        //noinspection unchecked
        this.proxy = (UP) Proxy.newProxyInstance(upClass.getClassLoader(),
                new Class[]{upClass, SystemMessageListener.class},
                handler);
        this.listener = listener;
        this.tcpClient = new VanillaTCPClient(name, socketAddresses, new ClientListener());
    }

    public void write(VanillaSignedMessage message) {
        try {
            if (message.protocol() == 0) {
                int pmt = registry.protocolMessageTypeFor(message.getClass());
                message.protocol(pmt >>> 16);
                message.messageType(pmt & MASK_16);
            }
            if (!message.signed()) {
                message.sign(secretKey);
            }
            tcpClient.write(message.byteBuffer());

        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    @Override
    public void write(BytesStore<?, ByteBuffer> bytes) throws IOException {
        tcpClient.write(bytes);
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        tcpClient.write(buffer);
    }

    @Override
    public void close() {
        tcpClient.close();
    }

    public boolean internal() {
        return internal;
    }

    public RPCClient internal(boolean internal) {
        this.internal = internal;
        return this;
    }

    class ClientListener implements TCPClientListener {

        @Override
        public void onMessage(TCPConnection client, Bytes bytes) throws IOException {
            bytes.readSkip(-4);
            try {
                parser.parseOne(bytes, listener);

            } catch (IORuntimeException iore) {
                if (iore.getCause() instanceof IOException)
                    throw (IOException) iore.getCause();
                throw iore;
            }
        }
    }
}
