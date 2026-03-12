import { Construct } from 'constructs';
import { Stack, Stage, StageProps, Environment } from 'aws-cdk-lib';
import * as pipelines from 'aws-cdk-lib/pipelines';

/**
 * Environment-specific configuration values parameterized per stage.
 * (Req 13.3, 19.5, 19.6)
 */
export interface StageConfig {
  /** AWS environment (account + region) for this stage */
  readonly env: Environment;
  /** SSM parameter name for the flight API key */
  readonly flightApiKeySsmParam: string;
  /** SSM parameter name for the Google Calendar credentials */
  readonly calendarCredentialsSsmParam: string;
  /** Notification recipient email address */
  readonly notificationEmail: string;
  /** Flight API base URL */
  readonly flightApiBaseUrl: string;
  /** Comma-separated list of destination airport codes */
  readonly destinations: string;
}

export interface PipelineConstructProps {
  /** CodeStar connection ARN for GitHub source (or CodeCommit repo name) */
  readonly connectionArn: string;
  /** GitHub owner/repo (e.g. 'myorg/flight-deal-notifier') */
  readonly repo: string;
  /** Branch to track */
  readonly branch: string;
  /** Dev stage configuration (Req 19.1, 19.5, 19.6) */
  readonly devConfig: StageConfig;
  /** Prod stage configuration (Req 19.1, 19.5, 19.6) */
  readonly prodConfig: StageConfig;
}

/**
 * CDK application stage that deploys the Flight Deal Notifier stack.
 * Used by the pipeline to deploy to Dev and Prod environments.
 * (Req 19.1, 19.5)
 */
export class FlightDealNotifierStage extends Stage {
  constructor(scope: Construct, id: string, props: StageProps & { stageConfig: StageConfig }) {
    super(scope, id, props);

    // The actual application stack will be composed here in Task 12.1.
    // For now, create a placeholder stack that the pipeline can deploy.
    new Stack(this, 'FlightDealNotifierStack', {
      env: props.stageConfig.env,
      description: `Flight Deal Notifier - ${id}`,
    });
  }
}

/**
 * CI/CD pipeline construct using CDK Pipelines.
 *
 * Pipeline stages:
 *   Source → Build (Gradle compile + unit tests + CDK synth) → Dev Deploy →
 *   Integration Tests → Prod Deploy
 *
 * (Req 13.2, 13.3, 13.4, 17.3, 17.4, 18.4, 18.5, 19.1–19.6, 20.5, 20.8, 20.9)
 */
export class PipelineConstruct extends Construct {
  public readonly pipeline: pipelines.CodePipeline;

  constructor(scope: Construct, id: string, props: PipelineConstructProps) {
    super(scope, id);

    // --- Source + Build + Synth ---
    // Source: CodeStar connection to GitHub (Req 20.8)
    // Build: Gradle compile + unit tests + CDK synth (Req 17.3, 17.4, 20.5, 20.6)
    const synthStep = new pipelines.ShellStep('Synth', {
      input: pipelines.CodePipelineSource.connection(props.repo, props.branch, {
        connectionArn: props.connectionArn,
      }),
      commands: [
        // Build Java service: compile + unit tests (Req 17.3, 17.4, 20.6)
        './gradlew -p service clean build',
        // Install CDK dependencies and synthesize (Req 20.7)
        'cd infra',
        'npm ci',
        'npx cdk synth',
      ],
      primaryOutputDirectory: 'infra/cdk.out',
    });

    // --- CodePipeline (Req 20.8, 20.9) ---
    this.pipeline = new pipelines.CodePipeline(this, 'BuildPipeline', {
      pipelineName: 'FlightDealNotifier-Pipeline',
      synth: synthStep,
      crossAccountKeys: true,
    });

    // --- Dev Stage deployment (Req 19.1, 19.2) ---
    const devStage = new FlightDealNotifierStage(this, 'Dev', {
      env: props.devConfig.env,
      stageConfig: props.devConfig,
    });

    const devDeployment = this.pipeline.addStage(devStage);

    // --- Integration tests against Dev Stage (Req 18.4, 18.5, 19.3, 19.4) ---
    // Runs after Dev deployment succeeds; failure halts pipeline and blocks Prod.
    devDeployment.addPost(
      new pipelines.ShellStep('IntegrationTests', {
        envFromCfnOutputs: {},
        commands: [
          // Run integration tests against the deployed Dev environment (Req 18.4)
          './gradlew -p integration-tests clean test',
        ],
      }),
    );

    // --- Prod Stage deployment, gated on integration test success (Req 19.3, 19.4) ---
    const prodStage = new FlightDealNotifierStage(this, 'Prod', {
      env: props.prodConfig.env,
      stageConfig: props.prodConfig,
    });

    this.pipeline.addStage(prodStage);
  }
}
