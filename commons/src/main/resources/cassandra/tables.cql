CREATE table contract_event(
id text primary key,
contract_id text,
time timestamp,
event_type text,
event_details blob
)

CREATE INDEX event_contract_id ON contract_event (contract_id);