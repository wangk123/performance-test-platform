## ADDED Requirements

### Requirement: Builtin function catalog completeness

The bundled `functions.json` SHALL include metadata entries for the full first-batch builtin set: `randomMobile`, `randomString`, `randomIdCard`, `randomBankCard`, `randomName`, `randomEmail`, `md5`, `sha256`, `base64Encode`, `base64Decode`, `urlEncode`.

#### Scenario: List API returns all first-batch keys

- **WHEN** an authenticated user sends `GET /api/jmeter-functions`
- **THEN** the response array SHALL include an object for each first-batch key listed above
- **AND** each object SHALL include `key`, `displayName`, `category`, `description`, `parameters`, and `example`

### Requirement: DATA and CODEC categories

Each `functions.json` entry SHALL use `category` value `DATA` for data-generation functions or `CODEC` for encode/digest functions.

#### Scenario: Categories assigned correctly

- **WHEN** the registry is loaded
- **THEN** `randomMobile`, `randomString`, `randomIdCard`, `randomBankCard`, `randomName`, and `randomEmail` SHALL have `category` `DATA`
- **AND** `md5`, `sha256`, `base64Encode`, `base64Decode`, and `urlEncode` SHALL have `category` `CODEC`
