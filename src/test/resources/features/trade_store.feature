Feature: Trade store
  Trades arrive in any order; the store keeps the latest version of each trade,
  rejects stale versions and matured trades, and expires trades once their maturity date passes.
  Scenarios use the sample data from the brief.

  Background:
    Given the trade store is empty

  Scenario: Sample trades from the brief, received before T1 matures
    Given today is "14/05/2020"
    When the following trades are received
      | tradeId | version | counterPartyId | bookId | maturityDate | createdDate |
      | T1      | 1       | CP-1           | B1     | 20/05/2020   |             |
      | T2      | 2       | CP-2           | B1     | 20/05/2021   |             |
      | T2      | 1       | CP-1           | B1     | 20/05/2021   | 14/03/2015  |
    Then trade "T2" version 1 is rejected with reason "LOWER_VERSION"
    And the trade store contains
      | tradeId | version | counterPartyId | bookId | maturityDate | createdDate | expired |
      | T1      | 1       | CP-1           | B1     | 20/05/2020   | 14/05/2020  | N       |
      | T2      | 2       | CP-2           | B1     | 20/05/2021   | 14/05/2020  | N       |
    And the audit trail for trade "T2" shows "ACCEPTED, REJECTED"

  Scenario: A trade with the same version replaces the stored record
    Given today is "14/05/2020"
    When the following trades are received
      | tradeId | version | counterPartyId | bookId | maturityDate |
      | T2      | 2       | CP-2           | B1     | 20/05/2021   |
      | T2      | 2       | CP-9           | B7     | 20/06/2021   |
    Then the trade store contains
      | tradeId | version | counterPartyId | bookId | maturityDate | expired |
      | T2      | 2       | CP-9           | B7     | 20/06/2021   | N       |

  Scenario: A higher version replaces the stored record
    Given today is "14/05/2020"
    When the following trades are received
      | tradeId | version | counterPartyId | bookId | maturityDate |
      | T2      | 1       | CP-1           | B1     | 20/05/2021   |
      | T2      | 2       | CP-2           | B1     | 20/05/2021   |
    Then the trade store contains
      | tradeId | version | counterPartyId |
      | T2      | 2       | CP-2           |

  Scenario: A trade whose maturity date is before today is rejected (T3 arriving late)
    Given today is "21/05/2014"
    When trade "T3" version 3 with maturity date "20/05/2014" is received
    Then trade "T3" version 3 is rejected with reason "MATURITY_DATE_IN_PAST"
    And trade "T3" is not in the store

  Scenario: A stored trade is marked expired once its maturity date passes (T3 row of the brief)
    Given today is "10/05/2014"
    When the following trades are received
      | tradeId | version | counterPartyId | bookId | maturityDate |
      | T3      | 3       | CP-3           | B2     | 20/05/2014   |
    And the business date moves to "21/05/2014" and the expiry job runs
    Then the trade store contains
      | tradeId | version | counterPartyId | bookId | maturityDate | createdDate | expired |
      | T3      | 3       | CP-3           | B2     | 20/05/2014   | 10/05/2014  | Y       |

  Scenario Outline: Maturity date boundary (today is 14/05/2020)
    Given today is "14/05/2020"
    When trade "B1" version 1 with maturity date "<maturity>" is received
    Then trade "B1" version 1 is <outcome>

    Examples:
      | maturity   | outcome                                             |
      | 15/05/2020 | accepted                                            |
      | 14/05/2020 | accepted                                            |
      | 13/05/2020 | rejected with reason "MATURITY_DATE_IN_PAST"        |
