import * as appconfig from 'aws-cdk-lib/aws-appconfig';
import { Construct } from 'constructs';
import { existsSync, readFileSync } from 'fs';
import { SERVICE_NAME } from '../configuration/constants';

export interface AppConfigConstructProps {
  readonly stage: string;
}

/**
 * Creates an AWS AppConfig application with a hosted freeform JSON configuration.
 * Config files are loaded from lib/config/{stage}.json with fallback to default.json.
 *
 * The Lambda extension fetches config from:
 *   http://localhost:2772/applications/{appId}/environments/{envId}/configurations/{profileId}
 */
export class AppConfigConstruct extends Construct {
  private static readonly DEFAULT_CONFIG_PATH = 'lib/config/default.json';

  public readonly application: appconfig.CfnApplication;
  public readonly environment: appconfig.CfnEnvironment;
  public readonly configurationProfile: appconfig.CfnConfigurationProfile;

  constructor(scope: Construct, id: string, props: AppConfigConstructProps) {
    super(scope, id);

    this.application = new appconfig.CfnApplication(this, 'Application', {
      name: SERVICE_NAME,
    });

    this.environment = new appconfig.CfnEnvironment(this, 'Environment', {
      applicationId: this.application.ref,
      name: props.stage,
    });

    const configContent = this.readConfigFile(props.stage);

    this.configurationProfile = new appconfig.CfnConfigurationProfile(this, 'ConfigProfile', {
      applicationId: this.application.ref,
      name: 'FlightSearchConfig',
      locationUri: 'hosted',
    });

    const version = new appconfig.CfnHostedConfigurationVersion(this, 'HostedConfig', {
      applicationId: this.application.ref,
      configurationProfileId: this.configurationProfile.ref,
      content: configContent,
      contentType: 'application/json',
      description: `Flight search config for ${props.stage}`,
    });

    // AllAtOnce deployment strategy (built-in, instant)
    new appconfig.CfnDeployment(this, 'Deployment', {
      applicationId: this.application.ref,
      environmentId: this.environment.ref,
      configurationProfileId: this.configurationProfile.ref,
      configurationVersion: version.ref,
      deploymentStrategyId: 'AppConfig.AllAtOnce',
    });
  }

  private readConfigFile(stage: string): string {
    const stagePath = `lib/config/${stage}.json`;
    if (existsSync(stagePath)) {
      return readFileSync(stagePath, { encoding: 'utf-8' });
    }
    return readFileSync(AppConfigConstruct.DEFAULT_CONFIG_PATH, { encoding: 'utf-8' });
  }
}
