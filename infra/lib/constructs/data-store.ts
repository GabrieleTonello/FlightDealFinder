import { Construct } from 'constructs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { RemovalPolicy } from 'aws-cdk-lib';
import { DYNAMODB } from '../configuration/constants';

export class DataStoreConstruct extends Construct {
  public readonly flightPriceHistoryTable: dynamodb.Table;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    this.flightPriceHistoryTable = new dynamodb.Table(this, 'FlightPriceHistory', {
      tableName: DYNAMODB.tableName,
      partitionKey: { name: DYNAMODB.partitionKey, type: dynamodb.AttributeType.STRING },
      sortKey: { name: DYNAMODB.sortKey, type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.DESTROY,
    });
  }
}
