-- Create a keyspace
CREATE KEYSPACE IF NOT EXISTS contract_store WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : '1' };

-- Create a table
CREATE TABLE IF NOT EXISTS contract_store.contract (
   id text PRIMARY KEY,
   type text,
   tradeDate date,
   settlementDate date,
   assetIdentifier text,
   assetIdentifierType text,
   quantity int,
   comments text
);

-- Insert some data
INSERT INTO contract_store.contract
(id,type,tradeDate,settlementDate,assetIdentifier,assetIdentifierType,quantity,comments)
VALUES ('1','LOAN',toDate('2021-06-01'),toDate('2021-06-04'),'ISIN','A11231232',100,'test trade 1');
INSERT INTO contract_store.contract
(id,type,tradeDate,settlementDate,assetIdentifier,assetIdentifierType,quantity,comments)
VALUES ('2','BORROW',toDate('2021-06-01'),toDate('2021-06-04'),'ISIN','A11231232',400,'test trade 2');
