-- El CEDEAR de Disney cotiza en BYMA como DISN, no DIS (error del seed V7).
-- Las transacciones apuntan al id del activo, así que no se ven afectadas.
UPDATE assets SET symbol = 'DISN' WHERE symbol = 'DIS';
