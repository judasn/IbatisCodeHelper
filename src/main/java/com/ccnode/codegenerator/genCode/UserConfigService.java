package com.ccnode.codegenerator.genCode;

import com.ccnode.codegenerator.pojo.DirectoryConfig;
import com.ccnode.codegenerator.pojo.GenCodeResponse;
import com.ccnode.codegenerator.util.GenCodeConfig;
import com.ccnode.codegenerator.util.IOUtils;
import com.ccnode.codegenerator.util.LoggerWrapper;
import com.ccnode.codegenerator.util.PojoUtil;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * What always stop you is what you always believe.
 * <p>
 * Created by zhengjun.du on 2016/05/21 12:40
 */
public class UserConfigService {

    private final static Logger LOGGER = LoggerWrapper.getLogger(UserConfigService.class);
    public static final String CONFIG_FILE_NAME = "codehelper.properties";

    public static Map<String, String> userConfigMap = Maps.newHashMap();


    public static void loadUserConfigNew(Project project, Module module) {
        PsiFile[] filesByName = FilenameIndex.getFilesByName(project, CONFIG_FILE_NAME, GlobalSearchScope.moduleScope(module));
        List<PsiFile> moduleFiles = new ArrayList<>();
        if (filesByName.length > 0) {
            for (PsiFile s : filesByName) {
                if (s.getVirtualFile().isWritable() && ModuleUtilCore.findModuleForPsiElement(s) == module) {
                    moduleFiles.add(s);
                }
            }
        }
        PsiFile wantedFile = null;
        if (moduleFiles.size() == 0) {
            return;
        } else if (moduleFiles.size() == 1) {
            wantedFile = moduleFiles.get(0);
        } else {
            //// TODO: 2016/12/15 show a dialog for user to choose.
            wantedFile = moduleFiles.get(0);
        }
        String path = wantedFile.getVirtualFile().getPath();
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(new File(path)));
            Set<String> strings = properties.stringPropertyNames();
            Map<String, String> userConfigMap = new HashMap<>();
            //all read from user config file.
            for (String s : strings) {
                //
                userConfigMap.put(s, properties.getProperty(s));
            }
            UserConfigService.userConfigMap = userConfigMap;
        } catch (Exception e) {
            // TODO: 2016/12/15 may show something.
            return;
        }
    }

    public static void loadUserConfig(String projectPath) {


        try {
            File propertiesFile = IOUtils.matchOnlyOneFile(projectPath, CONFIG_FILE_NAME);
            String fileName = StringUtils.EMPTY;
            if (propertiesFile != null) {
                fileName = propertiesFile.getAbsolutePath();
            }
            if (StringUtils.isBlank(fileName)) {
                return;
                //                return ret.failure("error, no generator.properties config file,"
                //                        + "please add an generator.properties in you poject path");
            }
            if (Objects.equal(fileName, "NOT_ONLY")) {
                LOGGER.error("error, duplicated codehelper.properties file");
                throw new RuntimeException("error, duplicated codehelper.properties file");
            }
            File configFile = new File(fileName);
            List<String> strings = IOUtils.readLines(configFile);
            strings = PojoUtil.avoidEmptyList(strings);
            int lineIndex = 1;
            Map<String, String> configMap = Maps.newHashMap();
            for (String configLine : strings) {
                lineIndex++;
                if (StringUtils.isBlank(configLine) || configLine.startsWith("#")) {
                    continue;
                }
                if (configLine.startsWith("=")) {
                    LOGGER.error("line: " + lineIndex + "error, config key con not be empty");
                    throw new RuntimeException("line: " + lineIndex + "error, config key con not be empty");
                }
                List<String> split = Splitter.on("=").trimResults().omitEmptyStrings().splitToList(configLine);
                if (split.size() != 2) {

                    LOGGER.error("", "line: " + lineIndex + "config error, correct format : key = value");
                    throw new RuntimeException("line: " + lineIndex + "config error, correct format : key = value");
                }
                configMap.put(split.get(0).toLowerCase(), split.get(1));
            }
            userConfigMap = configMap;
            //            ret.setUserConfigMap(configMap);
            LOGGER.info("readConfigFile configMap:{}", configMap);
        } catch (IOException e) {
            LOGGER.error(" readConfigFile config file read error ", e);
            throw new RuntimeException(" readConfigFile config file read error ");
        } catch (Exception e) {
            LOGGER.error(" readConfigFile config file read error ", e);
            throw new RuntimeException("readConfigFile error occurred");
        }
    }

    public static GenCodeResponse initConfig(GenCodeResponse response) {
        LOGGER.info("initConfig");
        if (response.getRequest().getInfo() != null) {
            return setResponseConfig(response, response.getUserConfigMap());
        }
        try {

            Map<String, String> userConfigMap = response.getUserConfigMap();
            String pojos = userConfigMap.get("pojos");
            if (StringUtils.isBlank(pojos)) {
                pojos = Messages.showInputDialog(response.getRequest().getProject(), "Please input Pojo Name : ", "Input Pojos", Messages.getQuestionIcon());
            }
            if (StringUtils.isBlank(pojos)) {
                return response.failure("no config or input pojo name");
            }
            pojos = pojos.replace(",", "|");
            pojos = pojos.replace("，", "|");
            pojos = pojos.replace(";", "|");
            pojos = pojos.replace("；", "|");
            response.getRequest().setPojoNames(Splitter.on("|").trimResults().omitEmptyStrings().splitToList(pojos));
            setResponseConfig(response, userConfigMap);
        } catch (Exception e) {
            LOGGER.error(" status error occurred :{}", response, e);
            return response.failure(" status error occurred");
        }

        return response;
    }

    private static GenCodeResponse setResponseConfig(GenCodeResponse response, Map<String, String> userConfigMap) {
        DirectoryConfig directoryConfig = new DirectoryConfig();
        response.setDirectoryConfig(directoryConfig);
        GenCodeConfig config = new GenCodeConfig();
        response.setCodeConfig(config);
        config.setDaoDir(removeStartAndEndSplitter(userConfigMap.get("dao.path")));
        config.setSqlDir(removeStartAndEndSplitter(userConfigMap.get("sql.path")));
        config.setMapperDir(removeStartAndEndSplitter(userConfigMap.get("mapper.path")));
        config.setServiceDir(removeStartAndEndSplitter(userConfigMap.get("service.path")));
        return response;
    }

    public static String removeStartAndEndSplitter(String s) {
        if (StringUtils.isBlank(s)) {
            return s;
        }
        String splitter = System.getProperty("file.separator");
        String ret = s;
        if (s.startsWith(splitter)) {
            ret = s.substring(1);
        }
        if (s.endsWith(splitter)) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }


    public static GenCodeResponse readConfigFile(String projectPath) {
        LOGGER.info("readConfigFile");
        GenCodeResponse ret = new GenCodeResponse();
        ret.accept();
        try {
            File propertiesFile = IOUtils.matchOnlyOneFile(projectPath, "codehelper.properties");
            String fileName = StringUtils.EMPTY;
            if (propertiesFile != null) {
                fileName = propertiesFile.getAbsolutePath();
            }
            if (StringUtils.isBlank(fileName)) {
                return ret;
            }
            if (Objects.equal(fileName, "NOT_ONLY")) {
                LOGGER.error("error, duplicated codehelper.properties file");
                return ret.failure("", "error, duplicated codehelper.properties file");
            }
            File configFile = new File(fileName);
            List<String> strings = IOUtils.readLines(configFile);
            strings = PojoUtil.avoidEmptyList(strings);
            int lineIndex = 1;
            Map<String, String> configMap = Maps.newHashMap();
            for (String configLine : strings) {
                lineIndex++;
                if (StringUtils.isBlank(configLine) || configLine.startsWith("#")) {
                    continue;
                }
                if (configLine.startsWith("=")) {
                    LOGGER.error("line: " + lineIndex + "error, config key con not be empty");
                    return ret.failure("", "line: " + lineIndex + "error, config key con not be empty");
                }
                List<String> split = Splitter.on("=").trimResults().omitEmptyStrings().splitToList(configLine);
                if (split.size() != 2) {

                    LOGGER.error("", "line: " + lineIndex + "config error, correct format : key = value");
                    return ret.failure("", "line: " + lineIndex + "config error, correct format : key = value");
                }
                configMap.put(split.get(0).toLowerCase(), split.get(1));
            }
            ret.setUserConfigMap(configMap);
            LOGGER.info("readConfigFile configMap:{}", configMap);
            return ret;
        } catch (IOException e) {
            LOGGER.error(" readConfigFile config file read error ", e);
            return ret.failure("", " readConfigFile config file read error ");
        } catch (Exception e) {
            LOGGER.error(" readConfigFile config file read error ", e);
            return ret.failure("", "readConfigFile error occurred");
        }
    }

    public static void main(String[] args) {
        String s = "dsfasdjfasdjf";
        System.out.println(s.substring(0, s.length() - 1));
    }
}
