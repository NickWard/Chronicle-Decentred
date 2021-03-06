package net.openhft.chronicle.decentred.dto;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.decentred.util.DtoParser;
import net.openhft.chronicle.decentred.util.DtoRegistry;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TransactionBlockEvent<T> extends VanillaSignedMessage<TransactionBlockEvent<T>> {
    private transient DtoParser<T> dtoParser;

    // for writing to a new set of bytes
    private transient Bytes writeTransactions = Bytes.allocateElasticDirect(4L << 10);

    // where to read transactions from
    private transient long messagesStart;
    private transient Bytes transactions;

    @IntConversion(UnsignedIntConverter.class)
    private short chainId; // up to 64K chains

    @IntConversion(UnsignedIntConverter.class)
    private short weekNumber; // up to 1256 years

    @IntConversion(UnsignedIntConverter.class)
    private int blockNumber; // up to 7k/s on average

    public TransactionBlockEvent() {
        transactions = writeTransactions.clear();
        messagesStart = 0;
    }

    public TransactionBlockEvent dtoParser(DtoParser<T> dtoParser) {
        this.dtoParser = dtoParser;
        return this;
    }

    @Override
    public void readMarshallable(BytesIn bytes) throws IORuntimeException {
        super.readMarshallable(bytes);
        messagesStart = bytes.readPosition();
        transactions = this.bytes;
    }

    public void replay(DtoRegistry<T> dtoRegistry, T allMessages) {
        if (dtoParser == null)
            dtoParser = dtoRegistry.get();
        replay(allMessages);
    }

    public void replay(T allMessages) {
        long p0 = transactions.readPosition();
        transactions.readPosition(messagesStart);
        long limit = transactions.readLimit();
        try {
            while (!transactions.isEmpty()) {
                long position = transactions.readPosition();
                long length = transactions.readUnsignedInt(position);
                transactions.readLimit(position + length);
                dtoParser.parseOne(transactions, allMessages);
                transactions.readLimit(limit);
                transactions.readSkip(length);
            }
        } finally {
            transactions.readLimit(limit);
            transactions.readPosition(p0);
        }
    }

    @Override
    public void reset() {
        super.reset();
        transactions = writeTransactions.clear();
        messagesStart = 0;
    }

    public TransactionBlockEvent addTransaction(VanillaSignedMessage message) {
        if (!message.signed())
            throw new IllegalArgumentException(message + " must be already signed");
        message.writeMarshallable(writeTransactions);
        return this;
    }

    @Override
    public void writeMarshallable0(BytesOut bytes) {
        super.writeMarshallable0(bytes);
        bytes.write(writeTransactions);
    }

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IORuntimeException {
        reset();
        super.readMarshallable(wire);
        wire.read("transactions").sequence(this, (tbe, in) -> {
            while (in.hasNextSequenceItem()) {
                tbe.addTransaction(in.object(VanillaSignedMessage.class));
            }
        });
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        super.writeMarshallable(wire);
        Class<T> superInterface = dtoParser.superInterface();
        wire.write("transactions").sequence(out -> replay((T) Proxy.newProxyInstance(superInterface.getClassLoader(), new Class[]{superInterface}, new AbstractMethodWriterInvocationHandler() {
            @Override
            protected void handleInvoke(Method method, Object[] args) {
                out.object(args[0]);
            }
        })));
    }

}