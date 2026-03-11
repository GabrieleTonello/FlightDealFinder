$version: "2"

namespace com.flightdeal.service

use com.flightdeal.model#FlightSearchResult
use com.flightdeal.model#CalendarLookupInput
use com.flightdeal.model#CalendarLookupOutput
use com.flightdeal.model#MatchInput
use com.flightdeal.model#MatchOutput
use com.flightdeal.model#NotificationInput
use com.flightdeal.model#NotificationOutput
use com.flightdeal.model#DealBatchMessage
use com.flightdeal.model#WorkflowStartResult
use com.flightdeal.model#PriceRecord
use com.flightdeal.model#DateRange
use com.flightdeal.model#TimeWindow

/// Umbrella service that includes all operations for unified code generation
service FlightDealNotifierService {
    version: "2025-01-01"
    operations: [SearchFlights, LookupCalendar, MatchFlights, SendNotification, StartWorkflow, StorePriceRecord]
}

service FlightSearchService {
    version: "2025-01-01"
    operations: [SearchFlights]
}

@readonly
operation SearchFlights {
    output: FlightSearchResult
}

service CalendarService {
    version: "2025-01-01"
    operations: [LookupCalendar]
}

@readonly
operation LookupCalendar {
    input: CalendarLookupInput
    output: CalendarLookupOutput
}

service FlightMatcherService {
    version: "2025-01-01"
    operations: [MatchFlights]
}

@readonly
operation MatchFlights {
    input: MatchInput
    output: MatchOutput
}

service NotificationService {
    version: "2025-01-01"
    operations: [SendNotification]
}

operation SendNotification {
    input: NotificationInput
    output: NotificationOutput
}

/// Operation to start the matching workflow with deal data
operation StartWorkflow {
    input: DealBatchMessage
    output: WorkflowStartResult
}

/// Operation to store a price record (ensures PriceRecord and DateRange types are generated)
operation StorePriceRecord {
    input: PriceRecord
}
