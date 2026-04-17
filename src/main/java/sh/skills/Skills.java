///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS sh.skills:jskills:LATEST

package sh.skills;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import sh.skills.commands.*;
import sh.skills.util.Console;

import java.util.concurrent.Callable;

/**
 * Main entry point for the skills CLI.
 * Mirrors src/cli.ts from the TypeScript source (vercel-labs/skills).
 *
 * Usage via JBang:
 *   jbang skills@maxandersen/jskills [command] [options]
 *
 * Or as a fat jar:
 *   java -jar jskills.jar [command] [options]
 */
@Command(
    name = "skills",
    description = "The CLI for the open agent skills ecosystem",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        AddCommand.class,
        ListCommand.class,
        RemoveCommand.class,
        CheckCommand.class,
        UpdateCommand.class,
        InitCommand.class,
        FindCommand.class,
        CommandLine.HelpCommand.class
    },
    footer = {
        "",
        "Examples:",
        "  skills add vercel-labs/agent-skills",
        "  skills add https://github.com/org/my-skills --agent claude-code",
        "  skills list",
        "  skills find web design",
        "  skills update",
        "  skills update my-skill          # update a single skill",
        "  skills update -g                 # update global skills only",
        "  skills remove my-skill",
        "  skills init my-new-skill",
        "",
        "Documentation: https://github.com/maxandersen/jskills",
        "Original project: https://github.com/vercel-labs/skills"
    }
)
public class Skills implements Callable<Integer> {

    @Option(names = {"--version"}, versionHelp = true, description = "Print version information")
    private boolean versionRequested;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Skills())
            .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                Console.error(ex.getMessage());
                return 1;
            })
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // No subcommand: show banner + usage hints (matching upstream showBanner)
        Console.showLogo();
        Console.log("");
        Console.log(Console.dim("The open agent skills ecosystem (ported to Java)"));
        Console.log("");
        bannerLine("skills add " + Console.dim("<package>"), "Add a new skill");
        bannerLine("skills remove",                          "Remove installed skills");
        bannerLine("skills list",                            "List installed skills");
        bannerLine("skills find " + Console.dim("[query]"),  "Search for skills");
        Console.log("");
        bannerLine("skills update",                          "Update installed skills");
        Console.log("");
        bannerLine("skills init " + Console.dim("<name>"),   "Create a new skill");
        Console.log("");
        Console.log(Console.dim("https://skills.sh"));
        Console.log("");
        return 0;
    }

    private static void bannerLine(String cmd, String desc) {
        // Pad command to 32 chars for alignment (strip ANSI for length calc)
        String plain = cmd.replaceAll("\u001B\\[[0-9;]*m", "");
        String padding = " ".repeat(Math.max(1, 32 - plain.length()));
        Console.log("  " + Console.dim("$") + " " + cmd + padding + Console.dim(desc));
    }
}
