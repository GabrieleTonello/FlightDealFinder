$version: "2"

namespace com.flightdeal.model

@documentation("Input to the Flight Matcher step in the matching workflow")
structure MatchInput {
    @required
    freeWindows: TimeWindowList

    @required
    deals: FlightDealList
}

@documentation("Output from the Flight Matcher step containing matched deals sorted by price")
structure MatchOutput {
    @required
    matchedDeals: FlightDealList
}

@documentation("Input to the Notification Service step in the matching workflow")
structure NotificationInput {
    @required
    matchedDeals: FlightDealList

    @required
    @documentation("Email address to send the deal notification to")
    recipientEmail: String
}

@documentation("Output from the Notification Service step")
structure NotificationOutput {
    @required
    success: Boolean

    @required
    @documentation("SES message ID of the sent email")
    messageId: String
}

@documentation("Result returned when a Step Functions workflow execution is started")
structure WorkflowStartResult {
    @required
    @documentation("ARN of the started Step Functions execution")
    executionArn: String
}
