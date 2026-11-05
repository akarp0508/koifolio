ALTER TABLE accounts ADD COLUMN cost_method text NOT NULL DEFAULT 'AVERAGE' CHECK (cost_method IN ('AVERAGE', 'FIFO'));
