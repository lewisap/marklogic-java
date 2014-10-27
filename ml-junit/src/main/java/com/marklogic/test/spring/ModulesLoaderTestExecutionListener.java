package com.marklogic.test.spring;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.configurer.ml7.RestApiModulesLoader;
import com.marklogic.client.helper.DatabaseClientProvider;

/**
 * Used to automatically load new/modified modules before a test runs.
 */
public class ModulesLoaderTestExecutionListener extends AbstractTestExecutionListener {

    private static boolean initialized = false;
    private final static Logger logger = LoggerFactory.getLogger(ModulesLoaderTestExecutionListener.class);

    /**
     * This currently only runs once; the thought is that an application will have an a base test
     * class that defines the module loaders for all subclasses. Could easily modify this to instead
     * keep track of which directories it has loaded already.
     */
    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        if (!initialized) {
            List<ModulesLoader> list = null;

            ModulesLoaders loaders = testContext.getTestClass().getAnnotation(ModulesLoaders.class);
            if (loaders != null) {
                list = Arrays.asList(loaders.configurers());
            } else {
                ModulesLoader loader = testContext.getTestClass().getAnnotation(ModulesLoader.class);
                if (loader != null) {
                    list = Arrays.asList(loader);
                }
            }

            if (list != null) {
                RestApiModulesLoader restApiModulesLoader = buildRestApiModulesLoader(testContext);
                for (ModulesLoader loader : list) {
                    String baseDir = loader.baseDir();
                    if (logger.isInfoEnabled()) {
                        logger.info(String.format("Loading modules, using base directory of %s", baseDir));
                    }
                    Set<File> loadedModules = restApiModulesLoader.loadModules(new File(baseDir));
                    if (loadedModules != null) {
                        testContext.getApplicationContext().publishEvent(new ModulesLoadedEvent(loadedModules));
                    }
                }
                initialized = true;
            }
        }
    }

    /**
     * Will first check for a RestApiModulesLoader in the Spring container.
     * 
     * @param testContext
     * @return
     */
    protected RestApiModulesLoader buildRestApiModulesLoader(TestContext testContext) {
        Map<String, RestApiModulesLoader> loaders = testContext.getApplicationContext().getBeansOfType(
                RestApiModulesLoader.class);
        if (loaders.size() == 1) {
            String name = loaders.keySet().iterator().next();
            logger.info("Found RestApiModulesLoader with name " + name + ", will use that for loading modules");
            return loaders.get(name);
        }

        DatabaseClientProvider p = testContext.getApplicationContext().getBean(DatabaseClientProvider.class);
        DatabaseClient client = p.getDatabaseClient();
        return new RestApiModulesLoader(client);
    }
}
