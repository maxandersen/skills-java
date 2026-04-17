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
        System.out.println();
        Console.log(Console.dim("The open agent skills ecosystem"));
        System.out.println();
        Console.log("  " + Console.dim("$") + " skills add " + Console.dim("<package>") + "        " + Console.dim("Add a new skill"));
        Console.log("  " + Console.dim("$") + " skills remove               " + Console.dim("Remove installed skills"));
        Console.log("  " + Console.dim("$") + " skills list                 " + Console.dim("List installed skills"));
        Console.log("  " + Console.dim("$") + " skills find " + Console.dim("[query]") + "         " + Console.dim("Search for skills"));
        System.out.println();
        Console.log("  " + Console.dim("$") + " skills update               " + Console.dim("Update installed skills"));
        System.out.println();
        Console.log("  " + Console.dim("$") + " skills init " + Console.dim("<name>") + "          " + Console.dim("Create a new skill"));
        System.out.println();
        Console.log(Console.dim("https://skills.sh"));
        System.out.println();
        return 0;
    }
}
