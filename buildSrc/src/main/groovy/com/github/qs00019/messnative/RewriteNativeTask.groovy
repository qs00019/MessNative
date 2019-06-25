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
        //类名与C文件替换列表，直接替换类名，例如替换在src/main/cpp/so.cpp里的com.test.So对应的com/test/So替换为a/b/c
        List<List<String>> classAndNativesMap = messExt.classAndNativesMap;
        //类名与C文件替换列表，替换方法名，例如替换在src/main/cpp/so.cpp里的test()为a()
        List<List<String>> classAllMethodAndNatives = messExt.classAllMethodAndNatives;

        //获取类名和c文件数组，下标一致，便于游标定位和判断是否包含元素
        def (List classNames1, List nativeNames1) = getArray(classAndNativesMap)
        def (List classNames2, List nativeNames2) = getArray(classAllMethodAndNatives)

        //保存混淆前的类名和混淆后的类名
        Map classMap1 = new LinkedHashMap<String,String>();
        //保存混淆前的类名，对应的方法列表和混淆后的方法列表
        Map<String, Map<String,String>> classMap2 = new LinkedHashMap<String,LinkedHashMap<String,String>>();

        MappingReader reader = new MappingReader(apkVariant.mappingFile)
        reader.pump(new MappingProcessor() {
            @Override
            boolean processClassMapping(String className, String newClassName) {
                //如果类名需要混淆，则未加入情况下，加入到map中
                if(classNames1.contains(className)){
                    if(!classMap1.containsKey(className)){
                        classMap1.put(className, newClassName);
                    }
                }
                //返回true，这个类的处理才能进入processMethodMapping
                return classNames2.contains(className)
            }

            @Override
            void processFieldMapping(String className, String fieldType, String fieldName, String newClassName, String newFieldName) {

            }

            @Override
            void processMethodMapping(String className, int firstLineNumber, int lastLineNumber, String methodReturnType, String methodName, String methodArguments, String newClassName, int newFirstLineNumber, int newLastLineNumber, String newMethodName) {
                //如果类名的方法需要混淆，将混淆前和混淆后的方法加入map
                if(!classMap2.containsKey(className)){
                    classMap2.put(className, new LinkedHashMap<String,String>())
                }
                classMap2.get(className).put( methodName , methodArguments + ";" + newMethodName);
            }
        })
        //标识是否进行了处理，因为有两项任务
        boolean flag = false;
        //保存被备份过的文件，如果前一项任务已经备份过，则不再备份
        List<String> fileList = new ArrayList<>()
        if(classMap1.size() != 0) {
            flag = true;
            nativeNames1.eachWithIndex { String nativeName, int i ->
                //nativeName对应的className，找到这个class里需要替换的方法map
                String oldClassName = classNames1.get(i)
                String newClassName = classMap1.get(oldClassName);
                //println "writeLine oldClassName:" + oldClassName + ";nativeName:" + nativeName

                //String nativePath = copyNativeFile(nativeName, fileList)
                String nativePath = "${project.projectDir.absolutePath}/" + nativeName
                if(!fileList.contains(nativeName)){
                    fileList.add(nativeName)
                    String bacupNativePath = nativePath + "~"
                    //println("nativePath:" + nativePath)
                    new File(bacupNativePath).delete()
                    new File(nativePath).renameTo(new File(bacupNativePath))
                    copyFile(bacupNativePath, nativePath)
                }

                //替换文本，注意在C里面是以/为分割符
                File f = new File(nativePath)
                String text = f.text;
                String oldStr = oldClassName.replace(".","/")
                String newStr = newClassName.replace(".","/")
                //if (line.contains("\"${oldStr}\"") && line.contains("\"${methodArgument}\"")) {
                if (text.contains("\"${oldStr}\"")) {
                    text = text.replace("\"${oldStr}\"", "\"${newStr}\"")
                    println("messnative replace oldStr:${oldStr},newStr:${newStr}")
                }
                f.delete()
                f.withWriter(CHARSET) { writer ->
                    writer.write(text)
                    writer.flush()
                    writer.close()
                }
            }
        }

        if(classMap2.size() != 0) {
            flag = true;
            nativeNames2.eachWithIndex { String nativeName, int i ->
                //nativeName对应的className，找到这个class里需要替换的方法map
                String className = classNames2.get(i)
                Map<String,String> map1 = classMap2.get(className);
                //println "writeLine oldClassName:" + oldClassName + ";nativeName:" + nativeName
                //String nativePath = copyNativeFile(nativeName, fileList)
                String nativePath = "${project.projectDir.absolutePath}/" + nativeName
                if(!fileList.contains(nativeName)){
                    fileList.add(nativeName)
                    String bacupNativePath = nativePath + "~"
                    //println("nativePath:" + nativePath)
                    new File(bacupNativePath).delete()
                    new File(nativePath).renameTo(new File(bacupNativePath))
                    copyFile(bacupNativePath, nativePath)
                }
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
                        println("messnative replace oldStr:${oldStr},newStr:${newStr}")
                    }

                }
                f.delete()
                f.withWriter(CHARSET) { writer ->
                    writer.write(text)
                    writer.flush()
                    writer.close()
                }
            }
        }
        //如果进入过两项任务之一，需需要重新编译C，且还原备份的文件
        if(flag){

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

            fileList.each { nativeName ->
                //println "nativeNames2.each nativeName:" + nativeName
                String nativePath = "${project.projectDir.absolutePath}/" + nativeName
                String bacupNativePath = nativePath + "~"
                //println "nativeNames2.each nativePath:" + nativePath
                new File(nativePath).delete()
                new File(bacupNativePath).renameTo(new File(nativePath))
            }
        }
        println "messnative end rewrite task"
    }

    private GString copyNativeFile(String nativeName, List<String> fileList) {
        String nativePath = "${project.projectDir.absolutePath}/" + nativeName
        if(!list.contains(nativeName)){
            list.add(nativeName)
            String bacupNativePath = nativePath + "~"
            //println("nativePath:" + nativePath)
            new File(bacupNativePath).delete()
            new File(nativePath).renameTo(new File(bacupNativePath))
            copyFile(bacupNativePath, nativePath)
        }
        nativePath
    }

    private List getArray(List<List<String>> classAndNativesMap) {
        List classNames = new ArrayList<String>();
        List nativeNames = new ArrayList<String>();
        classAndNativesMap.eachWithIndex { List<String> entry, int i ->
            classNames.add(entry.get(0))
            nativeNames.add(entry.get(1))
        }
        [classNames, nativeNames]
    }

    public static void copyFile(sourcePath, destationPath) {
        def src = new File(sourcePath)
        def dst = new File(destationPath)
        dst << src.text
    }
}
