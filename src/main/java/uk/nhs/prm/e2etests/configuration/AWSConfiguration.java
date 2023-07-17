package uk.nhs.prm.e2etests.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
public class AWSConfiguration {
    @Value("${aws.configuration.accessKey}")
    private String accessKey;

    @Value("${aws.configuration.secretAccessKey}")
    private String secretAccessKey;

    @Value("${aws.configuration.sessionToken}")
    private String sessionToken;

    @Value("${aws.configuration.region:#{'eu-west-2'}}")
    private String region;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return StaticCredentialsProvider
                .create(
                        AwsSessionCredentials
                                .create(this.accessKey, this.secretAccessKey, this.sessionToken)
                );
    }

    @Bean
    public SsmClient ssmClient() {
        return SsmClient
                .builder()
                .credentialsProvider(awsCredentialsProvider())
                .region(Region.EU_WEST_2)
                .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient
                .builder()
                .credentialsProvider(awsCredentialsProvider())
                .region(Region.EU_WEST_2)
                .build();
    }
}