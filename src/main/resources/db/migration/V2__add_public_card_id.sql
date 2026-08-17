ALTER TABLE cards
    ADD COLUMN public_id CHAR(36) NULL AFTER id;

UPDATE cards
   SET public_id = UUID()
 WHERE public_id IS NULL;

ALTER TABLE cards
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD CONSTRAINT uk_cards_public_id UNIQUE (public_id);
