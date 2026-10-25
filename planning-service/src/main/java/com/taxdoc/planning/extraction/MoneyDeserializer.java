package com.taxdoc.planning.extraction;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Reads money the way extraction sends it: a decimal string ("1196.25"), parsed straight into
 * {@code BigDecimal}. A JSON number is rejected rather than accepted: extraction never sends
 * one, so a number means the contract changed and someone should look (ADR-0003).
 */
final class MoneyDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            throw MismatchedInputException.from(
                    p, BigDecimal.class, "money must be a decimal string like \"1234.50\", got " + p.currentToken());
        }
        String text = p.getString();
        BigDecimal amount;
        try {
            amount = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw MismatchedInputException.from(p, BigDecimal.class, "not a decimal amount: \"" + text + "\"");
        }
        if (amount.signum() < 0) {
            throw MismatchedInputException.from(p, BigDecimal.class, "money must not be negative: " + text);
        }
        return amount;
    }
}
