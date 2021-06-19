create table CONTRACT_RH2(
 id varchar(100) primary key,
 type varchar(40),
 trade_date date,
 settlement_date date,
 asset_identifier varchar(200),
 asset_identifier_type varchar(50),
 quantity numeric,
 comments varchar(200)
);


create table CONTRACT_EVENT_RH2(
     id varchar(100) primary key,
     contract_id varchar(100),
     type varchar(40),
     event_date date,
     economic_change numeric,
     quantity numeric
);