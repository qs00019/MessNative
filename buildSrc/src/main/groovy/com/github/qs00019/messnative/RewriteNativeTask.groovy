package com.github.qs00019.messnative

import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import proguard.obfuscate.MappingProcessor
import proguard.obfuscate.MappingReader

import java.lang.reflect.Field
import java.nio.charset.StandardCharsets

class RewriteNativeTask extends DefaultTask {

    static final String CHARSET = StandardCharsets.UTF_8.name()

    @Input
    ApkVariant apkVariant

    @Input
    BaseVariantOutput variantOutput

    @Input
    MessExtension messExt

    @TaskAction
    void rewrite() {
        println "messnative start rewrite task"
        Map matchMap = messExt.map;
        if(matchMap == null || matchMap.size() == 0){
            println "messnative config map wrong"
            return
        }
        String[] nativeNames = new String[matchMap.size()];
        String[] classNames = new String[matchMap.size()];
        matchMap.eachWithIndex { Map.Entry<Object, Object> entry, int i ->
            classNames[i] = entry.getKey()
            nativeNames[i] = entry.getValue()
        }

        List<String> classList = Arrays.asList(classNames);
        Map<String, Map<String,String>> map = new LinkedHashMap<String,LinkedHashMap<String,String>>();
        MappingReader reader = new MappingReader(apkVariant.mappingFile)
        reader.pump(new MappingProcessor() {
            @Override
            boolean processClassMapping(String className, String newClassName) {
                return classList.contains(className)
            }

            @Override
            void processFieldMapping(String className, String fieldType, String fieldName, String newClassName, String newFieldName) {

            }

            @Override
            void processMethodMapping(String className, int firstLineNumber, int lastLineNumber, String methodReturnType, String methodName, String methodArguments, String newClassName, int newFirstLineNumber, int newLastLineNumber, String newMethodName) {
                if(!map.containsKey(className)){
                    map.put(className, new LinkedHashMap<String,String>())
                }
                map.get(className).put( methodName , methodArguments + ";" + newMethodName);
            }
        })

        if(map.size() != 0) {
            classNames.eachWithIndex { String className, int i ->
                Map<String,String> map1 = map.get(className);
                String nativeName = nativeNames[i]
                //println "writeLine className:" + className + ";nativeName:" + nativeName

                String nativePath = "${project.projectDir.absolutePath}/" + nativeName
                String bacupNativePath = nativePath + "~"
                //println("nativePath:" + nativePath)
                new File(bacupNativePath).delete()
                new File(nativePath).renameTo(new File(bacupNativePath))
                copyFile(bacupNativePath, nativePath)

                File f = new File(nativePath)
                String text = f.text;
                map1.each { k, v ->
                    String oldStr = k;
                    String[] value = v.split(";")
                    String methodArgument = value[0]
                    String newStr = value[1]
                    //if (line.contains("\"${oldStr}\"") && line.contains("\"${methodArgument}\"")) {
                    if (text.contains("\"${oldStr}\"")) {
                        text = text.replace("\"${oldStr}\"", "\"${newStr}\"")
                    }

                }
                f.delete()
                f.withWriter(CHARSET) { writer ->
                    writer.write(text)
                }


            }

            String taskName = "externalNativeBuild${apkVariant.name.capitalize()}"
            def externalNativeBuildTask = project.tasks.findByName(taskName)
            try {
                //this is for Android gradle 2.3.3 & above
                Field outcomeField = externalNativeBuildTask.state.getClass().getDeclaredField("outcome")
                outcomeField.setAccessible(true)
                outcomeField.set(externalNativeBuildTask.state, null)
            } catch (Throwable e) {
                externalNativeBuildTask.state.executed = false
            }
            externalNativeBuildTask.execute()

            nativeNames.each {nativeName ->
                //println "nativeNames.each nativeName:" + nativeName
                String nativePath = "${project.projectDir.absolutePath}/" + nativeName
                String bacupNativePath = nativePath + "~"
                //println "nativeNames.each nativePath:" + nativePath
                new File(nativePath).delete()
                new File(bacupNativePath).renameTo(new File(nativePath))
            }

        }
        println "messnative end rewrite task"
    }

    public static void copyFile(sourcePath, destationPath) {
        def src = new File(sourcePath)
        def dst = new File(destationPath)
        dst << src.text
    }
}
