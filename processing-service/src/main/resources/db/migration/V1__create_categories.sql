-- V1: Create categories table with seed data

CREATE TABLE categories (
    id BIGSERIAL NOT NULL,
    external_id UUID NOT NULL DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    parent_id BIGINT,
    mcc_codes VARCHAR(1024),
    keywords VARCHAR(1024),
    CONSTRAINT categories_pk PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_categories_external_id ON categories(external_id);
CREATE UNIQUE INDEX uq_categories_code ON categories(code);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_label ON categories (label);
CREATE INDEX idx_categories_path ON categories (path);
CREATE INDEX idx_categories_mcc ON categories (mcc_codes);
ALTER TABLE categories ADD CONSTRAINT categories_parent_fk FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE NO ACTION ON UPDATE NO ACTION;


-- Seed data: root categories + children

-- Root: Food & Dining
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'food_and_dining', 'Food & Dining', 'food_and_dining', NULL,
    'restaurant,cafe,coffee,food,eat,dining,bakery,pizza,sushi,burger'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'groceries', 'Groceries', 'food_and_dining.groceries', id,
    '5411,5422,5441,5451,5462,5499',
    'checkers,woolworths,pick n pay,spar,shoprite,food lover,grocery,supermarket,market'
FROM categories WHERE code = 'food_and_dining';

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'restaurants', 'Restaurants & Takeaways', 'food_and_dining.restaurants', id,
    '5812,5813,5814',
    'restaurant,takeaway,kfc,mcdonalds,steers,nandos,pizza,spur,wimpy,fishaways'
FROM categories WHERE code = 'food_and_dining';

-- Root: Transport
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'transport', 'Transport', 'transport', NULL,
    'uber,bolt,taxi,transport,petrol,fuel'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'fuel', 'Fuel & Petrol', 'transport.fuel', id,
    '5541,5542,5172',
    'petrol,engen,bp,shell,caltex,sasol,total,fuel'
FROM categories WHERE code = 'transport';

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'ride_hailing', 'Ride Hailing', 'transport.ride_hailing', id,
    '4121,4111',
    'uber,bolt,indriver,taxi,lyft,grab'
FROM categories WHERE code = 'transport';

-- Root: Shopping
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'shopping', 'Shopping', 'shopping', NULL,
    'shop,store,retail,buy'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'clothing', 'Clothing & Apparel', 'shopping.clothing', id,
    '5651,5661,5699,5621,5631',
    'zara,h&m,mr price,cotton on,ackermans,woolworths fashion,clothing,apparel,shoes'
FROM categories WHERE code = 'shopping';

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'electronics', 'Electronics', 'shopping.electronics', id,
    '5732,5734,5045,5065',
    'apple,samsung,game,incredible connection,takealot,laptop,phone,electronics'
FROM categories WHERE code = 'shopping';

-- Root: Utilities
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'utilities', 'Utilities', 'utilities', NULL,
    'electricity,water,rates,eskom,municipality'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'telecoms', 'Telecoms & Internet', 'utilities.telecoms', id,
    '4814,4812,4813,4899',
    'vodacom,mtn,telkom,cell c,rain,fibre,internet,airtime,data'
FROM categories WHERE code = 'utilities';

-- Root: Health
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'health', 'Health & Medical', 'health', NULL,
    'doctor,clinic,hospital,pharmacy,medical'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'pharmacy', 'Pharmacy', 'health.pharmacy', id,
    '5912,5047',
    'dischem,clicks,pharmacy,chemist,meds,medicine'
FROM categories WHERE code = 'health';

-- Root: Entertainment
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'entertainment', 'Entertainment', 'entertainment', NULL,
    'netflix,spotify,cinema,sport,gym'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'streaming', 'Streaming & Subscriptions', 'entertainment.streaming', id,
    '7372,5961',
    'netflix,spotify,dstv,showmax,apple music,youtube premium,disney,subscription'
FROM categories WHERE code = 'entertainment';

-- Root: Travel
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'travel', 'Travel', 'travel', NULL,
    'flight,hotel,airbnb,travel,holiday'
);

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'accommodation', 'Accommodation', 'travel.accommodation', id,
    '7011,7012',
    'hotel,airbnb,booking.com,marriott,hilton,guesthouse,lodge,accommodation'
FROM categories WHERE code = 'travel';

INSERT INTO categories (
    code, label, path, parent_id, mcc_codes, keywords
)
SELECT 
    'flights', 'Flights', 'travel.flights', id,
    '3000,3001,3002,4511',
    'kulula,flysafair,airlink,british airways,emirates,flight,airline'
FROM categories WHERE code = 'travel';

-- Fallback — always required
INSERT INTO categories (
    code, label, path, mcc_codes, keywords
)
VALUES (
    'uncategorised', 'Uncategorised', 'uncategorised', NULL, NULL
);

