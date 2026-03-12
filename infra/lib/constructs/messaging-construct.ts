import { Construct } from 'constructs';
import { Duration } from 'aws-cdk-lib';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';

export class MessagingConstruct extends Construct {
  public readonly dealTopic: sns.Topic;
  public readonly dealQueue: sqs.Queue;
  public readonly deadLetterQueue: sqs.Queue;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    // Dead Letter Queue — 14-day message retention (Req 5.3)
    this.deadLetterQueue = new sqs.Queue(this, 'DeadLetterQueue', {
      queueName: 'FlightDealDLQ',
      retentionPeriod: Duration.days(14),
    });

    // Deal Queue — visibility timeout >= 6x Lambda timeout (120s × 6 = 720s) (Req 5.4)
    // Redrive policy: maxReceiveCount=3, then move to DLQ (Req 5.2, 14.2)
    this.dealQueue = new sqs.Queue(this, 'DealQueue', {
      queueName: 'FlightDealQueue',
      visibilityTimeout: Duration.seconds(720),
      deadLetterQueue: {
        queue: this.deadLetterQueue,
        maxReceiveCount: 3,
      },
    });

    // SNS Deal Topic (Req 5.1)
    this.dealTopic = new sns.Topic(this, 'DealTopic', {
      topicName: 'FlightDealTopic',
    });

    // Subscribe the Deal Queue to the Deal Topic (Req 5.1)
    this.dealTopic.addSubscription(
      new subscriptions.SqsSubscription(this.dealQueue),
    );
  }
}
