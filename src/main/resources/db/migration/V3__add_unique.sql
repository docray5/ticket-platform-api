ALTER TABLE venues ADD CONSTRAINT uq_venues_name UNIQUE (name);
ALTER TABLE venues ADD CONSTRAINT uq_venues_address UNIQUE (address);

ALTER TABLE movies ADD CONSTRAINT uq_movies_title UNIQUE (title);