-- Red de la tarjeta de crédito (VISA / Mastercard) que informa el banco emisor.
-- Nullable: si el emisor no la informa, la app muestra la tarjeta sin logo de red.
ALTER TABLE pagos.tarjeta_credito
    ADD COLUMN marca VARCHAR(12) CHECK (marca IN ('visa', 'mastercard'));
