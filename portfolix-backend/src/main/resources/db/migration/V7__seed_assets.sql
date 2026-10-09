-- Catálogo inicial. Acciones con el ticker de BYMA; CEDEARs con el ticker con el que cotizan en BYMA.
-- Acciones y CEDEARs se operan en ARS; cripto en USD.

-- Acciones argentinas
INSERT INTO assets (symbol, name, type, currency) VALUES
    ('GGAL',  'Grupo Financiero Galicia',          'STOCK', 'ARS'),
    ('YPFD',  'YPF S.A.',                          'STOCK', 'ARS'),
    ('PAMP',  'Pampa Energía',                     'STOCK', 'ARS'),
    ('BMA',   'Banco Macro',                       'STOCK', 'ARS'),
    ('BBAR',  'BBVA Argentina',                    'STOCK', 'ARS'),
    ('SUPV',  'Grupo Supervielle',                 'STOCK', 'ARS'),
    ('VALO',  'Grupo Financiero Valores',          'STOCK', 'ARS'),
    ('BYMA',  'Bolsas y Mercados Argentinos',      'STOCK', 'ARS'),
    ('TXAR',  'Ternium Argentina',                 'STOCK', 'ARS'),
    ('ALUA',  'Aluar',                             'STOCK', 'ARS'),
    ('CEPU',  'Central Puerto',                    'STOCK', 'ARS'),
    ('EDN',   'Edenor',                            'STOCK', 'ARS'),
    ('TRAN',  'Transener',                         'STOCK', 'ARS'),
    ('TGSU2', 'Transportadora de Gas del Sur',     'STOCK', 'ARS'),
    ('TECO2', 'Telecom Argentina',                 'STOCK', 'ARS'),
    ('LOMA',  'Loma Negra',                        'STOCK', 'ARS'),
    ('CRES',  'Cresud',                            'STOCK', 'ARS'),
    ('COME',  'Sociedad Comercial del Plata',      'STOCK', 'ARS'),
    ('MIRG',  'Mirgor',                            'STOCK', 'ARS');

-- CEDEARs
INSERT INTO assets (symbol, name, type, currency) VALUES
    ('AAPL',  'Apple Inc.',                        'CEDEAR', 'ARS'),
    ('MSFT',  'Microsoft Corp.',                   'CEDEAR', 'ARS'),
    ('GOOGL', 'Alphabet Inc.',                     'CEDEAR', 'ARS'),
    ('AMZN',  'Amazon.com',                        'CEDEAR', 'ARS'),
    ('NVDA',  'NVIDIA Corp.',                      'CEDEAR', 'ARS'),
    ('TSLA',  'Tesla Inc.',                        'CEDEAR', 'ARS'),
    ('META',  'Meta Platforms',                    'CEDEAR', 'ARS'),
    ('MELI',  'MercadoLibre',                      'CEDEAR', 'ARS'),
    ('NFLX',  'Netflix',                           'CEDEAR', 'ARS'),
    ('AMD',   'Advanced Micro Devices',            'CEDEAR', 'ARS'),
    ('INTC',  'Intel Corp.',                       'CEDEAR', 'ARS'),
    ('KO',    'Coca-Cola',                         'CEDEAR', 'ARS'),
    ('MCD',   'McDonald''s',                       'CEDEAR', 'ARS'),
    ('WMT',   'Walmart',                           'CEDEAR', 'ARS'),
    ('DIS',   'Walt Disney',                       'CEDEAR', 'ARS'),
    ('JPM',   'JPMorgan Chase',                    'CEDEAR', 'ARS'),
    ('V',     'Visa',                              'CEDEAR', 'ARS'),
    ('BRKB',  'Berkshire Hathaway',                'CEDEAR', 'ARS'),
    ('XOM',   'Exxon Mobil',                       'CEDEAR', 'ARS'),
    ('PFE',   'Pfizer',                            'CEDEAR', 'ARS'),
    ('BABA',  'Alibaba',                           'CEDEAR', 'ARS'),
    ('GLOB',  'Globant',                           'CEDEAR', 'ARS'),
    ('VIST',  'Vista Energy',                      'CEDEAR', 'ARS'),
    ('SPY',   'SPDR S&P 500 ETF',                  'CEDEAR', 'ARS'),
    ('QQQ',   'Invesco QQQ (Nasdaq 100)',          'CEDEAR', 'ARS');

-- Criptomonedas
INSERT INTO assets (symbol, name, type, currency) VALUES
    ('BTC',   'Bitcoin',                           'CRYPTO', 'USD'),
    ('ETH',   'Ethereum',                          'CRYPTO', 'USD'),
    ('USDT',  'Tether',                            'CRYPTO', 'USD'),
    ('USDC',  'USD Coin',                          'CRYPTO', 'USD'),
    ('SOL',   'Solana',                            'CRYPTO', 'USD'),
    ('ADA',   'Cardano',                           'CRYPTO', 'USD'),
    ('BNB',   'BNB',                               'CRYPTO', 'USD'),
    ('XRP',   'XRP',                               'CRYPTO', 'USD'),
    ('DOGE',  'Dogecoin',                          'CRYPTO', 'USD'),
    ('DOT',   'Polkadot',                          'CRYPTO', 'USD'),
    ('AVAX',  'Avalanche',                         'CRYPTO', 'USD'),
    ('LINK',  'Chainlink',                         'CRYPTO', 'USD'),
    ('LTC',   'Litecoin',                          'CRYPTO', 'USD'),
    ('POL',   'Polygon',                           'CRYPTO', 'USD');
