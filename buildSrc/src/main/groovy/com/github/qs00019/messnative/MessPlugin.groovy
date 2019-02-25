package com.github.qs00019.messnative

import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.BaseVariantOutput
import org.gradle.api.Plugin
import org.gradle.api.Project

class MessPlugin implements Plugin<Project> {


    private static final String PLUGIN_NAME = 'messnative'

    @Override
    void apply(Project project) {
        MessExtension ext = project.extensions.create(PLUGIN_NAME, MessExtension.class)

        project.afterEvaluate {
            project.plugins.withId('com.android.application') {
                project.android.applicationVariants.all { ApkVariant variant ->

                    String taskName1 = "externalNativeBuild${variant.name.capitalize()}"
                    def externalNativeBuildTask = project.tasks.findByName(taskName1)
                    if(externalNativeBuildTask == null){
                        println "no externalNativeBuild return"
                        return
                    }
                    variant.outputs.each { BaseVariantOutput output ->

                        String taskName = "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
                        def proguardTask = project.tasks.findByName(taskName)
                        if (!proguardTask) {
                            return
                        }

                        proguardTask.doLast {
                            println "proguard finish, ready to execute rewrite"
                            RewriteNativeTask rewriteTask = project.tasks.create(name: "rewriteNativeFor${variant.name.capitalize()}",
                                    type: RewriteNativeTask
                            ) {
                                apkVariant = variant
                                variantOutput = output
                                messExt = ext
                            }
                            rewriteTask.execute()
                        }

                    }
                }
            }
        }
    }
}
