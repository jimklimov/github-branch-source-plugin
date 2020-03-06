package org.jenkinsci.plugins.github_branch_source;
// mvn -Dtest=GithubSCMSourcePRedBranchesIndexingTest -DtestLogging.showStandardStreams=true test || echo FAILED

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class GithubSCMSourcePRedBranchesIndexingTest {
	private static Logger log = Logger.getLogger(GithubSCMSourcePRedBranchesIndexingTest.class.getName());

    public GithubSCMSourcePRedBranchesIndexingTest() {
        this.source = new GitHubSCMSource("cloudbeers", "yolo", null, false);
    }

	// The following branches are predefined in the mock files used below :
    public static SCMHead branchMaster = new BranchSCMHead("master");
    public static SCMHead branchWithPR = new BranchSCMHead("branchWithPR");
    public static SCMHead branchWithoutPR = new BranchSCMHead("branchWithoutPR");
    public static SCMHead branchRelease= new BranchSCMHead("release/version-123");
    public static SCMHead branchHotfix = new BranchSCMHead("hotfix/BUGID-123456");
	// TODO: Extend to testing also with patterned branch-name filters

    public static PullRequestSCMHead prMerge = new PullRequestSCMHead(
		"", "cloudbeers", "yolo", "branchWithPR",
        1, (BranchSCMHead) branchMaster,
        SCMHeadOrigin.DEFAULT, ChangeRequestCheckoutStrategy.MERGE);

    /**
     * All tests in this class only use Jenkins for the extensions
     */                 
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

	public static WireMockRuleFactory factory = new WireMockRuleFactory();

    @Rule
    public WireMockRule githubRaw = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("raw")
    );
    @Rule
    public WireMockRule githubApi = factory.getRule(WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("api")
            .extensions(
                    new ResponseTransformer() {
                        @Override
                        public Response transform(Request request, Response response, FileSource files,
                                                  Parameters parameters) {
                            if ("application/json"
                                    .equals(response.getHeaders().getContentTypeHeader().mimeTypePart())) {
                                return Response.Builder.like(response)
                                        .but()
                                        .body(response.getBodyAsString()
                                                .replace("https://api.github.com/",
                                                        "http://localhost:" + githubApi.port() + "/")
                                                .replace("https://raw.githubusercontent.com/",
                                                        "http://localhost:" + githubRaw.port() + "/")
                                        )
                                        .build();
                            }
                            return response;
                        }
        
                        @Override
                        public String getName() {
                            return "url-rewrite";
                        }
                        
                    })  
    );
    private GitHubSCMSource source;
    GitHub github;
    GHRepository repo;

	@Before
	public void prepareMockGitHub() throws Exception {
/*
        new File("src/test/resources/api/mappings").mkdirs();
        new File("src/test/resources/api/__files").mkdirs();
        new File("src/test/resources/raw/mappings").mkdirs();
        new File("src/test/resources/raw/__files").mkdirs();
        githubApi.enableRecordMappings(new SingleRootFileSource("src/test/resources/api/mappings"),
                new SingleRootFileSource("src/test/resources/api/__files"));
        githubRaw.enableRecordMappings(new SingleRootFileSource("src/test/resources/raw/mappings"),
                new SingleRootFileSource("src/test/resources/raw/__files"));
*/

		// PRs
        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/pulls"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr-from-branch-listing.json")));

        githubApi.stubFor(
                get(urlEqualTo("/repos/cloudbeers/yolo/pulls/226"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../PRs/_files/body-yolo-pulls-open-pr-from-branch-226.json")));

		// Branches
        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-listing.json")));

        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo/master"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-master.json")));

        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo/branchWithPR"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-branchWithPR.json")));

        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo/branchWithoutPR"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-branchWithoutPR.json")));

        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo/branchRelease"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-branchRelease.json")));

        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo/branchHotfix"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-branchHotfix.json")));

        githubApi.stubFor(
                get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("https://api.github.com/")));
        githubRaw.stubFor(get(urlMatching(".*")).atPriority(10)
                .willReturn(aResponse().proxiedFrom("https://raw.githubusercontent.com/")));

        source = new GitHubSCMSource(null, "http://localhost:" + githubApi.port(), GitHubSCMSource.DescriptorImpl.SAME, null, "cloudbeers", "yolo");
        github = Connector.connect("http://localhost:" + githubApi.port(), null);
        repo = github.getRepository("cloudbeers/yolo");

		System.err.println("Did setup for prepareMockGitHub()");
	}

	// The initial couple of methods are here to make sure the indended mocks are seen
    @Test
    public void testIndexing__see__allOnePR() throws IOException {
        // Situation: Hitting the Github API for a PR listing and getting the open PR #216
//		try {prepareMockGitHub();} catch (Throwable t) { throw new IOException("FAILED to prepareMockGitHub(): " + t.toString()); }
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true),
 				new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
				new ForkPullRequestDiscoveryTrait.TrustContributors())));
        githubApi.stubFor(
                get(urlEqualTo("/branches/cloudbeers/yolo"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json; charset=utf-8")
                                        .withBodyFile("../branches/_files/body-yolo-branches-with-pr-from-branch-listing.json")));


        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantPRs();
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);
        Iterator<GHPullRequest> pullRequest = new GitHubSCMSource("cloudbeers", "yolo", null, false)
                .new LazyPullRequests(request, repo).iterator();

		System.err.println("=== Looking at PR iterator " + pullRequest.toString());

        // Expected: In the iterator will have one item in it
        assertTrue(pullRequest.hasNext());
        assertEquals(216, pullRequest.next().getId());
        
        assertFalse(pullRequest.hasNext());
    }

    @Test
    public void testIndexing__want__allBranches() throws IOException {
        // Situation: Hitting the Github API for a branch listing and getting all those present in repo
//		try {prepareMockGitHub();} catch (Throwable t) { throw new IOException("FAILED to prepareMockGitHub(): " + t.toString()); }
        source.setTraits(Arrays.asList(new BranchDiscoveryTrait(true, true),
 				new ForkPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.MERGE),
				new ForkPullRequestDiscoveryTrait.TrustContributors())));

		String[] expectedBranchNamesArr = {
			branchMaster.getName(),
			branchWithPR.getName(),
			branchWithoutPR.getName(),
			branchRelease.getName(),
			branchHotfix.getName()
		};
		List<String> expectedBranchNames = Arrays.asList(expectedBranchNamesArr);
		System.err.println("=== Expecting branches: " + expectedBranchNames.toString());

        SCMHeadObserver mockSCMHeadObserver = Mockito.mock(SCMHeadObserver.class);
        GitHubSCMSourceContext context = new GitHubSCMSourceContext(null, mockSCMHeadObserver);
        context.wantBranches(true);
        GitHubSCMSourceRequest request = context.newRequest(new GitHubSCMSource("cloudbeers", "yolo", null, false), null);

        // Expected: We see all branches defined in mock and none others
		long count = 0;
        for (GHBranch branch : new GitHubSCMSource.LazyBranches(request, repo)) {
//        for (GHBranch branch : request.getBranches()) {
			System.err.println("=== Looking at branch: " + branch.toString() + " (" + branch.getName() + ")");
			// Is the name we saw among those expected?
//			assertTrue(0 < Arrays.binarySearch(expectedBranchNames, branch.getName()));
			assertTrue(expectedBranchNames.contains(branch.getName()));
			count++;
		}

		System.err.println("Counted " + count + " branches");
		// Have we seen all expected names - no more, no less?
		assertEquals(expectedBranchNames.size(), count);
    }

/* TODO: per discussion in https://github.com/jenkinsci/github-branch-source-plugin/pull/284
   the idea is along the lines of setting up a mock github repo with a master
   branch and at least two other branches, one of which has a PR to master;
   and somehow invoke the branch-indexing runs with the different strategies
   (three of them?) and check that the expected amount of branches were
   discovered, and that the indexing log contains expected lines from
   isExcluded() where applicable.

   I assumed that except the last "indexing log" part, a test setup like this
   should already exist, but did not find one to extend.
   Logically BranchDiscoveryTraitTest.java looks like the place, but it seems
   to only care that expected Java classes for filtering get assigned to the
   call stack for different setups - and does not actually mock/run the indexing.
*/
}
