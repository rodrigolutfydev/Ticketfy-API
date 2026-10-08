package com.lutfy.ticketfy.payout.account;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PayoutAccountValidator implements ConstraintValidator<ValidPayoutAccount, PayoutAccountUpdateDTO> {

    @Override
    public boolean isValid(PayoutAccountUpdateDTO dto, ConstraintValidatorContext context) {
        if (dto == null) return true;
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (dto.documentType() != null && dto.document() != null && !dto.document().isBlank()
                && !BrazilianDocuments.isValid(dto.documentType(), dto.document())) {
            context.buildConstraintViolationWithTemplate("Invalid " + dto.documentType())
                    .addPropertyNode("document").addConstraintViolation();
            valid = false;
        }
        if (dto.pixKeyType() != null && dto.pixKey() != null && !dto.pixKey().isBlank()
                && PixKeys.normalize(dto.pixKeyType(), dto.pixKey()).isEmpty()) {
            context.buildConstraintViolationWithTemplate("Invalid pix key for type " + dto.pixKeyType())
                    .addPropertyNode("pixKey").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
