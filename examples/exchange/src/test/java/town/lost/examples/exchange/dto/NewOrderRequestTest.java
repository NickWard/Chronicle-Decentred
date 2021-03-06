package town.lost.examples.exchange.dto;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.decentred.util.DecentredUtil;
import net.openhft.chronicle.salt.Ed25519;
import net.openhft.chronicle.wire.Marshallable;
import org.junit.Test;
import town.lost.examples.exchange.api.CurrencyPair;
import town.lost.examples.exchange.api.DtoAliases;
import town.lost.examples.exchange.api.Side;

import static org.junit.Assert.assertEquals;

public class NewOrderRequestTest {
    static {
        DtoAliases.addAliases();
    }

    @Test
    public void marshallable() {
        BytesStore privateKey1 = DecentredUtil.testPrivateKey(7);
        Bytes publicKey1 = Bytes.allocateDirect(Ed25519.PUBLIC_KEY_LENGTH);
        Bytes secretKey1 = Bytes.allocateDirect(Ed25519.SECRET_KEY_LENGTH);
        Ed25519.privateToPublicAndSecret(publicKey1, secretKey1, privateKey1);

        NewOrderRequest nor = new NewOrderRequest()
                .action(Side.BUY)
                .currencyPair(CurrencyPair.EURXCL)
                .maxPrice(1000.0)
                .quantity(1000.0)
                .ttlMillis(300)
                .protocol(1)
                .messageType(1001)
                .sign(secretKey1, new SetTimeProvider("2018-03-04T18:03:05.364453"));
        assertEquals("!NewOrderRequest {\n" +
                "  timestampUS: 2018-03-04T18:03:05.364453,\n" +
                "  address: phccofmpy6ci,\n" +
                "  quantity: 1E3,\n" +
                "  maxPrice: 1E3,\n" +
                "  currencyPair: EURXCL,\n" +
                "  ttlMillis: 300.0,\n" +
                "  action: BUY\n" +
                "}\n", nor.toString());
        NewOrderRequest nor2 = Marshallable.fromString(nor.toString());
        assertEquals(nor2, nor);
    }

}