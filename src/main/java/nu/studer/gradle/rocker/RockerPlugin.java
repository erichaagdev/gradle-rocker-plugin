package nu.studer.gradle.rocker;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class RockerPlugin implements Plugin<Project> {

    private static final String ROCKER_VERSION_PROPERTY = "rockerVersion";
    private static final String DEFAULT_ROCKER_VERSION = "0.16.0";

    private static final Logger LOGGER = LoggerFactory.getLogger(RockerPlugin.class);

    @Override
    public void apply(final Project project) {
        // apply Java base plugin, making it possible to also use the rocker plugin for Android builds
        project.getPlugins().apply(JavaBasePlugin.class);

        // allow to configure the rocker version via extension property
        project.getExtensions().getExtraProperties().set(ROCKER_VERSION_PROPERTY, DEFAULT_ROCKER_VERSION);

        // use the configured rocker version on all rocker dependencies
        enforceRockerVersion(project);

        // add rocker DSL extension
        NamedDomainObjectContainer<RockerConfig> container = project.container(RockerConfig.class, new NamedDomainObjectFactory<RockerConfig>() {
            @Override
            public RockerConfig create(String name) {
                return new RockerConfig(name, project);
            }
        });
        project.getExtensions().add("rocker", container);

        // create configuration for the runtime classpath of the rocker compiler (shared by all rocker configuration domain objects)
        final Configuration configuration = createRockerCompilerRuntimeConfiguration(project);

        // create a rocker task for each rocker configuration domain object
        container.all(new Action<RockerConfig>() {
            @Override
            public void execute(RockerConfig config) {
                // create task
                RockerCompile rocker = project.getTasks().create("rocker" + capitalize(config.name), RockerCompile.class);
                rocker.setGroup("Rocker");
                rocker.setDescription("Invokes the Rocker template engine.");
                rocker.config = config;
                rocker.rockerCompilerRuntime = configuration;

                // wire task dependencies such that the rocker task creates the sources before the corresponding Java compile task compiles them
                SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
                SourceSet sourceSet = sourceSets.findByName(config.name);
                if (sourceSet != null) {
                    project.getTasks().getByName(sourceSet.getCompileJavaTaskName()).dependsOn(rocker);
                    project.getDependencies().add(sourceSet.getCompileConfigurationName(), "com.fizzed:rocker-runtime");
                }
            }
        });
    }

    private Configuration createRockerCompilerRuntimeConfiguration(Project project) {
        Configuration rockerCompilerRuntime = project.getConfigurations().create("rockerCompiler");
        rockerCompilerRuntime.setDescription("The classpath used to invoke the Rocker template engine. Add your additional dependencies here.");
        project.getDependencies().add(rockerCompilerRuntime.getName(), "com.fizzed:rocker-compiler");
        project.getDependencies().add(rockerCompilerRuntime.getName(), "org.slf4j:slf4j-simple:1.7.23");
        return rockerCompilerRuntime;
    }

    private void enforceRockerVersion(final Project project) {
        project.getConfigurations().all(new Action<Configuration>() {
            @Override
            public void execute(Configuration configuration) {
                configuration.resolutionStrategy(new Action<ResolutionStrategy>() {
                    @Override
                    public void execute(ResolutionStrategy resolutionStrategy) {
                        resolutionStrategy.eachDependency(new Action<DependencyResolveDetails>() {
                            @Override
                            public void execute(DependencyResolveDetails details) {
                                ModuleVersionSelector requested = details.getRequested();
                                if (requested.getGroup().equals("com.fizzed") && requested.getName().startsWith("rocker-")) {
                                    details.useVersion(project.getExtensions().getExtraProperties().get(ROCKER_VERSION_PROPERTY).toString());
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}
