package com.lutfy.ticketfy.payout.account;

import com.lutfy.ticketfy.infra.validation.ValidationCodes;
import com.lutfy.ticketfy.infra.validation.ValidationPayload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class PayoutAccountValidator implements ConstraintValidator<ValidPayoutAccount, PayoutAccountUpdateDTO> {

    @Override
    public boolean isValid(PayoutAccountUpdateDTO dto, ConstraintValidatorContext context) {
        if (dto == null) return true;
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (dto.documentType() != null && dto.document() != null && !dto.document().isBlank()
                && !BrazilianDocuments.isValid(dto.documentType(), dto.document())) {
            ValidationPayload.attach(context, ValidationCodes.INVALID_DOCUMENT,
                    Map.of("documentType", dto.documentType().name()));
            context.buildConstraintViolationWithTemplate("Invalid " + dto.documentType())
                    .addPropertyNode("document").addConstraintViolation();
            valid = false;
        }
        if (dto.pixKeyType() != null && dto.pixKey() != null && !dto.pixKey().isBlank()
                && PixKeys.normalize(dto.pixKeyType(), dto.pixKey()).isEmpty()) {
            ValidationPayload.attach(context, ValidationCodes.INVALID_PIX_KEY,
                    Map.of("pixKeyType", dto.pixKeyType().name()));
            context.buildConstraintViolationWithTemplate("Invalid pix key for type " + dto.pixKeyType())
                    .addPropertyNode("pixKey").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
