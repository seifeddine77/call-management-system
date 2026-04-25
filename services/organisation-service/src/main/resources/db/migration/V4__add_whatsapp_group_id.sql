-- V4: Ajouter le champ whatsapp_group_id pour lier l'agence à son groupe WhatsApp
ALTER TABLE agences ADD COLUMN whatsapp_group_id VARCHAR(100);
