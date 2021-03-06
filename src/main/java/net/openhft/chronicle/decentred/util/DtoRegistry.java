package net.openhft.chronicle.decentred.util;

import net.openhft.chronicle.bytes.MethodId;
import net.openhft.chronicle.core.Maths;
import net.openhft.chronicle.core.util.ObjectUtils;
import net.openhft.chronicle.decentred.api.Verifier;
import net.openhft.chronicle.decentred.dto.VanillaSignedMessage;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DtoRegistry<T> implements Supplier<DtoParser<T>> {
    public static final int MASK_16 = 0xFFFF;

    private final Class<T> superInterface;
    private final Map<Class, Integer> classToProtocolMessageType = new LinkedHashMap<>();
    private final IntObjMap<DtoParselet> parseletMap = IntObjMap.withExpectedSize(DtoParselet.class, 128);

    private DtoRegistry(Class<T> superInterface) {
        this.superInterface = superInterface;
        addProtocol(0xFF00, (Class) Verifier.class);
        addProtocol(0xFF01, (Class) Verifier.class);
    }

    public static <T> DtoRegistry<T> newRegistry(Class<T> superInterface) {
        return new DtoRegistry<>(superInterface);
    }

    public DtoRegistry<T> addProtocol(int protocol, Class<? super T> pClass) {
        for (Method method : pClass.getDeclaredMethods()) {
            MethodId mid = method.getAnnotation(MethodId.class);
            if (mid != null) {
                assert (mid.value() | MASK_16) == MASK_16;
                int key = (int) ((protocol << 16) + mid.value());
                try {
                    parseletMap.put(key,
                            new DtoParselet(method, protocol, Maths.toUInt16(mid.value())));
                    classToProtocolMessageType.put(method.getParameterTypes()[0], key);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return this;
    }

    public int protocolFor(Class clazz) {
        return protocolMessageTypeFor(clazz) >>> 16;
    }

    public int messageTypeFor(Class clazz) {
        return protocolMessageTypeFor(clazz) & MASK_16;
    }

    public int protocolMessageTypeFor(Class clazz) {
        Integer pmt = classToProtocolMessageType.get(clazz);
        if (pmt == null) throw new IllegalStateException(clazz + " not defined");
        return pmt;
    }

    @Override
    public DtoParser<T> get() {
        IntObjMap<DtoParselet> parseletMap2 = IntObjMap.withExpectedSize(DtoParselet.class, parseletMap.size() * 2);
        parseletMap.forEach((i, dp) -> parseletMap2.put(i, new DtoParselet(dp)));
        return new VanillaDtoParser<>(superInterface, parseletMap2);
    }

    public <T extends VanillaSignedMessage<T>> T create(Class<T> tClass) {
        int pmt = protocolMessageTypeFor(tClass);
        try {
            int protocol = pmt >>> 16;
            int messageType = pmt & MASK_16;
            T vsm = ObjectUtils.newInstance(tClass);
            return vsm.protocol(protocol).messageType(messageType);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
