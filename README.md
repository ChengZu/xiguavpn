别的vpn不会用，自己写了一个，傻瓜都会用
# 如何配置服务器（Ubuntu）

1.安装jdk
```  
 apt-get install openjdk-8-jdk
```  
2.运行程序
 
 将build/EasyVpn.jar 拷贝到服务器,执行下面命令运行
```  
 java -jar EasyVpn.jar
```  

## 断开ssh后仍运行，可使用screen

 安装screen
 ```  
  apt-get install screen
 ```  
 创建新会话
 ```  
  screen -S vpn
 ```  
 运行vpn
 ```  
  java -jar EasyVpn.jar

#screen -r vpn //恢复会话

#screen -X -S vpn quit //完全删除会话

#快捷键命令：先同时按Ctrl+A+D键，这样退出的话，以后还可以通过screen -r （name）再次进入
```  


# 如何安装客户端

1.将build/app-release.apk 拷贝到android手机上安装

2.ip选项填服务器IP地址,其他选项默认即可

3.点击启动

# 可以翻墙的vps服务商 [vultr](https://www.vultr.com/?ref=9126507-8H)

2022.5月 ssh的22端口还可以用，那么就关闭ssh开vpn，没有ssh用控制面板网页版的vnc
```
//关闭ssh命令
/etc/init.d/ssh stop
```  
不要安装jdk1.8，运行报错，安装jdk11
```
apt install openjdk-11-jdk-headless
```
运行程序，用22端口
```
 java -jar EasyVpn.jar 22
```
