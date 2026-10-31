package com.example.iot.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubDemandDispatcher {
    private final String token, repository, ref, workflow;
    private final RestClient client;

    public GitHubDemandDispatcher(@Value("${demand.github.token:}") String token,
            @Value("${demand.github.repository:}") String repository,
            @Value("${demand.github.ref:main}") String ref,
            @Value("${demand.github.workflow:demand-batch.yml}") String workflow) {
        this.token=token; this.repository=repository; this.ref=ref; this.workflow=workflow;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client=RestClient.builder().baseUrl("https://api.github.com").requestFactory(factory).build();
    }

    public boolean configured() { return !token.isBlank() && repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") && workflow.matches("[A-Za-z0-9_.-]+\\.ya?ml"); }

    public void dispatch() {
        if (!configured()) throw new IllegalStateException("Configure DEMAND_GITHUB_TOKEN and DEMAND_GITHUB_REPOSITORY");
        client.post().uri("/repos/"+repository+"/actions/workflows/"+workflow+"/dispatches")
                .header("Authorization","Bearer "+token)
                .header("Accept","application/vnd.github+json")
                .header("X-GitHub-Api-Version","2022-11-28")
                .body(Map.of("ref",ref)).retrieve().toBodilessEntity();
    }
}
