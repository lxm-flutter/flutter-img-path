# flutter-img-path

一个Flutter 图片路径创建插件

灵感源自于https://github.com/Leo0618/flutter-img-sync

+ step1.创建图片存放目录 如: ./assets/home/img

+ step2.修改pubspec.yaml</b><br>
  ```yaml
    assets:
      assets:
      # assets-generator-begin
      # lib/assets/home/img/*
      # assets-generator-end
  ```
+ step3.Tools--Flutter Img Path

  pubspec.yaml中的assets变量会被配置项目的图片，并在./lib目录下生产r.dart文件
  > 如果没效果。可以Ctrl + S 保存一下，或者关闭文件再打开 

+ step4.代码中导入r.dart文件，然后引用R类的变量

快捷键：Alt + I

