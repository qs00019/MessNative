# MessNative

## 背景
Android JNI接口混淆方案。以下文章描述了java暴露jni接口的问题
https://www.jianshu.com/p/0350cb715934
https://www.jianshu.com/p/676861ca29fd

本项目参考了Mess，对native方法进行了混淆，然后改变RegisterNatives注册java的方法字符串为混淆后的字符串，达到混淆native方法的目的。实现原理见
https://www.jianshu.com/p/799e5bc62633


## 使用方法

```groovy

dependencies {
        classpath 'com.android.tools.build:gradle:3.3.0'
        classpath 'com.github.qs00019:messnative:1.0.1'
   }
    
apply plugin: 'com.android.application'
apply plugin: 'com.github.qs00019.messnative'

//java类名和C中动态注册的文件名	
messnative {
    map = ["com.github.qs00019.messnative.TestSo":"src/main/cpp/native-lib.cpp"]
}
```

复制android SDK下proguard目录下的proguard-android.txt到当前目录，需要注释native方法的默认混淆
```
#-keepclasseswithmembernames class * {
#    native <methods>;
#}
```

build.gradle中修改
```
proguardFiles getDefaultProguardFile('proguard-android.txt'),  'proguard-rules.pro'
```
```
proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
```

需要防止TestSo被混淆，proguard增加

```
-keep class com.github.qs00019.messnative.TestSo
```


## 注意
在C中替换java方法名时，groovy返回的methodSignature是java语法，C里动态注册用的是smali语法。当前版本未做方法签名判断，所以有方法重写是，替换回出错。

## Sample示例
示例工程混淆后反编译，SoTest类的方法被混淆
![示例图片](https://github.com/qs00019/MessNative/blob/master/pic.jpg)
