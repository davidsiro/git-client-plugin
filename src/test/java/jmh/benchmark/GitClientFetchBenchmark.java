package jmh.benchmark;

import hudson.EnvVars;
import hudson.model.TaskListener;
import jenkins.benchmark.jmh.JmhBenchmark;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * A JMH micro-benchmark performance test, it aims to compare the performance of git-fetch using both "git" and "jgit"
 * implementations represented by CliGitAPIImpl and JGitAPIImpl respectively.
 */
//@JmhBenchmark
public class GitClientFetchBenchmark {

    @State(Scope.Benchmark)
    public static class JenkinsState {

        @Param({"git", "jgit"})
        String gitExe;

        /**
         * We test the performance of git fetch on four repositories, varying them on the basis of their
         * commit history size, number of branches and ultimately their overall size.
         * Java-logging-benchmarks: (0.034 MiB) https://github.com/stephenc/java-logging-benchmarks.git
         * Coreutils: (4.58 MiB) https://github.com/uutils/coreutils.git
         * Cairo: (93.54 MiB) https://github.com/cairoshell/cairoshell.git
         * Samba: (324.26 MiB) https://github.com/samba-team/samba.git
         */
        @Param({"https://github.com/stephenc/java-logging-benchmarks.git",
                "https://github.com/uutils/coreutils.git",
                "https://github.com/freedesktop/cairo.git",
                "https://github.com/samba-team/samba.git"})
        String repoUrl;

        final FolderForBenchmark tmp = new FolderForBenchmark();
        File gitDir;
        File localRemoteDir;
        File remoteRepoDir;
        GitClient gitClient;
        List<RefSpec> refSpecs  = new ArrayList<>();
        URIish urIish;

        private File cloneUpstreamRepositoryLocally(File parentDir, String repoUrl) throws Exception {
            String repoName = repoUrl.split("/")[repoUrl.split("/").length - 1];
            File gitRepoDir = new File(parentDir, repoName);
            gitRepoDir.mkdir();
            GitClient cloningGitClient = Git.with(TaskListener.NULL, new EnvVars()).in(gitRepoDir).using(gitExe).getClient();
            cloningGitClient.clone_().url(repoUrl).execute();
            assertTrue("Unable to create git repo", gitRepoDir.exists());
            return gitRepoDir;
        }

        /**
         * We want to create a temporary local git repository after each iteration of the benchmark, similar to
         * "before" and "after" JUnit annotations.
         */
        @Setup(Level.Iteration)
        public void doSetup() throws Exception {
            tmp.before();
            gitDir = tmp.newFolder();
            localRemoteDir = tmp.newFolder();
            remoteRepoDir = cloneUpstreamRepositoryLocally(localRemoteDir, repoUrl);

            gitClient = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using(gitExe).getClient();

            // Coreutils is a repo sized 4.58 MiB, Cairo is 93.64 MiB and samba is 324.26 MiB
            urIish = new URIish("file://" + remoteRepoDir.getAbsolutePath());

            // fetching all branches
            refSpecs.add(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));

            // initialize the test folder for git fetch
            gitClient.init();

            System.out.println("Do Setup");
        }

        @TearDown(Level.Iteration)
        public void doTearDown() {
            try {
                // making sure that git init made a git an empty repository
                File gitDir = gitClient.withRepository((repo, channel) -> repo.getDirectory());
                System.out.println(gitDir.isDirectory());
            } catch (Exception e){
                e.getMessage();
            }
            tmp.after();
            System.out.println("Do TearDown");
        }
    }

    @Benchmark
    public void gitFetchBenchmark(JenkinsState jenkinsState, Blackhole blackhole) throws Exception {
        FetchCommand fetch = jenkinsState.gitClient.fetch_().from(jenkinsState.urIish, jenkinsState.refSpecs);
        fetch.execute();
        blackhole.consume(fetch);
    }
}