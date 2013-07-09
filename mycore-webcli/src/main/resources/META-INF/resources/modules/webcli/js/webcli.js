// Load Dojo's code relating to the Button widget
dojo.require("dojo.parser");
dojo.require("dijit.layout.LayoutContainer");
dojo.require("dijit.layout.TabContainer");
dojo.require("dijit.layout.ContentPane");
dojo.require("dijit.form.Button");
dojo.require("dijit.DropDownMenu");
dojo.require("dijit.Toolbar");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form.Slider");
dojo.require("dijit.form.TextBox");
dojo.require("dijit.form.Textarea");
dojo.require("dijit.Dialog");
dojo.require("dojox.string.sprintf");
if (webCLIServlet==null){
	var webCLIServlet='../../servlets/MCRWebCLIServlet';
	console.debug("webCLIServlet is not set trying: "+webCLIServlet);
}

function RingBuffer(/*int*/ size){
	this.maxSize=size;
	this.curSize=0;
	this.buffer=new Array();
	this.add=function(/*String*/ str){
		if (this.curSize == this.maxSize) this.buffer.shift();
		else this.curSize++;
		this.buffer.push(str);
	}
	this.clear=function(){
		this.buffer.splice(0,this.curSize);
		this.curSize = 0;
	}
	this.getEntries=function(){
		return this.buffer.slice(0,this.curSize)
	}
	this.resize=function(/*int*/ size){
		if (size == this.maxSize) return;
		webcli.logDebug("Resize ring buffer from "+ this.maxSize+" to "+size);
		if (this.maxSize < size) {
			for (var i = this.maxSize; i < size; i++){
				this.buffer.shift();
			}
		}
		this.maxSize = size;
	}
}

var test;
var webcli={
	"failedData"	:	null,
	"failedIoArgs"	:	null,
	"doRefresh"		:	true,
	"debug"			:	true,
	"logDebug"		:	function(/*String*/ str){
		if (webcli.debug==true){
			if (window.console.debug){
				console.debug(str);
			} else if (window.console.log){
				console.log(str);
			} else {
				alert(str);
			}
		}
	},
	"servletError"	:	function(data, ioArgs) {
		webcli.failedData=data;
		webcli.failedIoArgs=ioArgs;
		webcli.logDebug("Return status "+ioArgs.xhr.status+": "+ioArgs.xhr.statusText);
		if (data.message){
			webcli.logDebug("Error message: "+data.message);
		}
		webcli.logDebug("Response: "+ioArgs.xhr.responseText);
		webcli.stopRefresh();
		var title = ioArgs.xhr.status+": "+ioArgs.xhr.statusText;
		var dialogText = ioArgs.xhr.responseText;
		if (ioArgs.xhr.status<100 || ioArgs.xhr.status>999){
			title = "Server is not responding";
			dialogText = "Received bad return code '"+ioArgs.xhr.status+"' which mostly means that the server is down."
		}
		var errorDialog= new dijit.Dialog({
			title: title
		});
		if (data.message){
			dialogText+="<br/>Browser Message:<p style=\"color:red\">"+data.message+"</p>";
		}
		errorDialog.setContent(dialogText);
		errorDialog.layout();
		errorDialog.show();
	},
	"refreshPos"	:   4,
	"playButton"	:	null,
	"pauseButton"	:	null,
	"initRefreshPos":	function(leftSiblingId){
		webcli.logDebug("getting insert Position")
		var toolbar=dijit.registry.byId("toolbar");
		var i=0;
		dojo.forEach(toolbar.getChildren(), function(widget){
			webcli.logDebug("widget "+ (i++)+" id = "+widget.id);
			if (widget.id==leftSiblingId){
				webcli.logDebug("found widget "+leftSiblingId+ " at position "+i);
				webcli.refreshPos=i;
				return;
			}
		});
	},
	"toggleRefresh"	:	function(){
		webcli.logDebug("toggle...");
		if (webcli.doRefresh==true){
			webcli.stopRefresh();
		} else {
			webcli.startRefresh();
		}
	},
	"stopRefresh"	:	function(){
		webcli.logDebug("toggle off");
		webcli.doRefresh=false;
		var toolbar=dijit.registry.byId("toolbar");
		var i=0;
		webcli.logDebug("pauseButton widget: "+webcli.pauseButton);
		dojo.forEach(toolbar.getChildren(), function(widget){
			webcli.logDebug("widget "+ (i++)+" = "+widget);
			if (widget==webcli.pauseButton){
					webcli.logDebug("remove pausebutton");
					toolbar.removeChild(webcli.pauseButton);
					webcli.logDebug("add playbutton");
					toolbar.addChild(webcli.playButton, webcli.refreshPos);
			}
		});
		webcli.logDebug("stop: webcli.doRefresh = "+webcli.doRefresh);
		window.clearInterval(webcli.commands.refreshHandler);
		window.clearInterval(webcli.logs.refreshHandler);
	},
	"startRefresh"	:	function(){
		webcli.logDebug("toggle on");
		webcli.doRefresh=true;
		var toolbar=dijit.registry.byId("toolbar");
		var i=0;
		webcli.logDebug("playButton widget: "+webcli.playButton);
		dojo.forEach(toolbar.getChildren(), function(widget){
			webcli.logDebug("widget "+ (i++)+" = "+widget);
			if (widget==webcli.playButton){
				webcli.logDebug("remove playbutton");
				toolbar.removeChild(webcli.playButton);
				webcli.logDebug("add pausebutton");
				toolbar.addChild(webcli.pauseButton, webcli.refreshPos);
			}
		});
		webcli.logDebug("start: webcli.doRefresh = "+webcli.doRefresh);
		webcli.commands.refreshHandler = window.setInterval("webcli.commands.refreshQueue()",  webcli.commands.refreshRate);
		webcli.logs.refreshHandler = window.setInterval("webcli.logs.refresh()", webcli.logs.refreshRate);
	},
	"commands"	:	{
		"refreshRate"					:	5000,
		"knownCommands"					:	null,
		"refreshReady"					:	true,
		"_callbackKnownCommands"		:	function(data, ioArgs) {
			webcli.commands.knownCommands= dojo.fromJson(data).commands;
			webcli.gui.createCommandsMenu();
		},
		"selectCommand"					:	function(/*String*/ evt) {
			var selectedCommand;
			if (evt.target.textContent){
				selectedCommand = evt.target.textContent;
			} else {
				//IE goes here
				selectedCommand = evt.target.innerText;
			}
			dijit.registry.byId("command").setValue(selectedCommand);
		},
		"_callbackRun"					:	function(data, ioArgs) {
			webcli.logDebug("command appended");
		},
		"executeCommand"				:	function(/*String*/ cmd) {
			webcli.logDebug("Will execute:\n"+cmd);
			dojo.xhrGet({
				url: webCLIServlet,
				load: webcli.commands._callbackRun,
				error: webcli.servletError,
				content: {run: cmd}
			});
		},
		"captureCommand"				:	function(/*Event*/ evt) {
			if (evt.keyCode == 13){
				webcli.commands.executeCommand(dojo.byId("command").value);
			}
		},
		"commandQueue"					:	null,
		"_callbackCommandQueue"			:	function(data, ioArgs) {
			var commandQueue = dojo.fromJson(data).commandQueue;
			if ((webcli.commands.commandQueue!=null) && 
				(commandQueue.length==webcli.commands.commandQueue.length)){
				if ((commandQueue.length==0) || 
					((commandQueue.length>0) && (commandQueue[0]==webcli.commands.commandQueue[0]))){
					webcli.logDebug("_callbackCommandQueue: nothing todo current queue is the same as server reported queue");
				}
			}
			webcli.commands.commandQueue=dojo.fromJson(data).commandQueue;
			var list = document.createElement("ol");
			dojo.forEach(webcli.commands.commandQueue, function(command){
				var newLink=document.createElement("li");
				newLink.appendChild(document.createTextNode(command));
				list.appendChild(newLink);
			});
			var cEl=dijit.registry.byId("commandQueue");
			cEl.setContent(list);
			webcli.commands.refreshReady=true;
		},
		"refreshHandler"			: null,
		"refreshQueue"				: function(){
			webcli.logDebug("commands: webcli.doRefresh = "+webcli.doRefresh);
			if (webcli.doRefresh==true){
				if (webcli.commands.refreshReady==true){
					webcli.commands.refreshReady=false;
					dojo.xhrGet({
						url: webCLIServlet,
						load: webcli.commands._callbackCommandQueue,
						error: webcli.servletError,
						preventCache: true,
						content: {request: "getCommandQueue"}
					});
				}
			} else {
				//some error occured on previous server request, stop automatic refreshing
				webcli.logDebug("refreshQueue: 'webcli.doRefresh!=true' abort automatic refresh");
				window.clearInterval(webcli.commands.refreshHandler);
			}
		},
		"getRefreshRate"	:	function(){
			return webcli.commands.refreshRate;
		},
		"setRefreshRate"	:	function(/*int*/ rate){
			if (webcli.commands.refreshRate!=rate){
				webcli.logDebug("setting command queue refresh rate: "+rate);
				webcli.commands.refreshRate=rate;
				window.clearInterval(webcli.commands.refreshHandler);
				webcli.commands.refreshHandler=window.setInterval("webcli.commands.refreshQueue()", rate);
			}
		}
	},
	"gui"		:	{
		"createCommandsMenu"	:	function(){
			var menu = dijit.registry.byId("commandMenu");
			dojo.forEach(menu.getChildren(), function(widget){
				webcli.logDebug("removing "+" = "+widget);
				menu.removeChild(widget);
			});
			var templateString = '<div style="max-height: 500px; overflow-y: auto; overflow-x: hidden">' + new dijit.DropDownMenu().templateString + '</div>';
			dojo.forEach(webcli.commands.knownCommands, function(commandGroup){
				var submenu = new dijit.DropDownMenu({
				  templateString: templateString
				});
				var popup=new dijit.PopupMenuItem({
					label: commandGroup.name,
					popup: submenu
				});
				menu.addChild(popup);
				dojo.forEach(commandGroup.commands,function(command){
					var subMenuItem=new dijit.MenuItem({
						label: command,
						onClick: webcli.commands.selectCommand
					});
					submenu.addChild(subMenuItem);
				});
			});
			var params= {
				label: "Command",
				dropDown: menu
			};
			menu.startup();
		},
		"processSettings"		:	function(dialogFields){
			webcli.logs.setRefreshRate(dialogFields.logRefreshSetting);
			webcli.commands.setRefreshRate(dialogFields.queueRefreshSetting);
			webcli.logs.ringBuffer.resize(Math.floor(dialogFields.logHistorySize));
		},
		"handleSlider"			:	function(/*String*/ id, value){
			element = dojo.byId(id);
			element.value = value;
			label = dojo.byId(id+".label").getElementsByTagName("div")[0].getElementsByTagName("div")[0];
			if (id == 'logHistorySize') {
				label.firstChild.nodeValue = document.createTextNode(dojox.string.sprintf("%d\u00A0lines",value)).nodeValue;
			} else {
				label.firstChild.nodeValue = document.createTextNode(dojox.string.sprintf("%.2f\u00A0s",(value/1000))).nodeValue;
			}
		},
		"showSettings"			:	function(){
			dijit.registry.byId('settingsDialog').show();
			dijit.registry.byId('logRefreshSetting').setValue(webcli.logs.getRefreshRate(), true);
			dijit.registry.byId('queueRefreshSetting').setValue(webcli.commands.getRefreshRate(), true);
			dijit.registry.byId('logHistorySize').setValue(webcli.logs.ringBuffer.maxSize, true);
		}
	},
	"logs"	:	{
		"refreshRate"		:	1000,
		"autoScroll"		:	true,
		"refreshReady"		:	true,
		"startTime"			:	null,
		"ringBuffer"		:	new RingBuffer(50),
		"logBuffer"		:   null,
		"_callback"			:	function(data, ioArgs) {
			webcli.logDebug("webcli.logs._callback started");
			var midTime=new Date();
			var logs= dojo.fromJson(data).logs;
			if (logs.length > 0){
				webcli.logDebug("writing logs");
			}
			//remove logs that will never be displayed
			while (logs.length > webcli.logs.ringBuffer.maxSize)
				logs.shift();
			webcli.logs.logBuffer=logs;
			webcli.logDebug("enabled logs.refreshReady");
			webcli.logs.refreshReady=true;
			//webcli.logs.refreshHandler = window.setInterval("webcli.logs.refresh()", webcli.logs.refreshRate);
			var endTime=new Date();
			if (logs.length > 0){
				webcli.logs.updateLogLines();
				webcli.logDebug("count logs:"+logs.length+"   midTime="+(midTime.getTime() - webcli.logs.startTime.getTime())+" ms,    endTime="+(endTime.getTime()-midTime.getTime())+" ms");
			}
		},
		"toggleAutoScroll"	:	function(){
			if (webcli.logs.autoScroll==true){
				webcli.logDebug("Switching autoscroll off");
				webcli.logs.autoScroll=false;
			} else {
				webcli.logDebug("Switching autoscroll on");
				webcli.logs.autoScroll=true;
			}
		},
		"getRefreshRate"	:	function(){
			return webcli.logs.refreshRate;
		},
		"setRefreshRate"	:	function(/*int*/ rate){
			if (webcli.logs.refreshRate!=rate){
				webcli.logDebug("setting command queue refresh rate: "+rate);
				webcli.logs.refreshRate=rate;
				window.clearInterval(webcli.logs.refreshHandler);
				webcli.logs.refreshHandler=window.setInterval("webcli.logs.refresh()", rate);
			}
		},
		"clear"			:	function(){
			var logElement=dojo.byId("logs").getElementsByTagName("pre")[0];
			if (logElement.hasChildNodes()){
				while (logElement.childNodes.length > 0) {
					logElement.removeChild(logElement.firstChild);
				}
			}
		},
		"updateLogLines"	:	function() {
			var logElement=dojo.byId("logs").getElementsByTagName("pre")[0];
			var oldSize=webcli.logs.ringBuffer.curSize;
			dojo.forEach(webcli.logs.logBuffer, function(line){
				webcli.logs.ringBuffer.add(line);
				logElement.appendChild(document.createTextNode(line.logLevel+": "+line.message+"\r\n"));
			});
			var curSize = oldSize + webcli.logs.logBuffer.length;
			//remove old log lines
			while (curSize > webcli.logs.ringBuffer.maxSize){
				logElement.removeChild(logElement.firstChild);
				curSize--;
			}
			if (webcli.logs.autoScroll==true){
				logElement.parentNode.scrollTop = logElement.parentNode.scrollHeight - logElement.parentNode.offsetHeight + 100;
			}
		},
		"refreshHandler"	:	null,
		"refresh"			:	function(){
			webcli.logDebug("logs: webcli.doRefresh = "+webcli.doRefresh);
			if (webcli.doRefresh==true){
				//window.clearInterval(webcli.logs.refreshHandler);
				if (webcli.logs.refreshReady==true){
					webcli.logs.refreshReady=false;
					webcli.logs.startTime = new Date();
					dojo.xhrGet({
						url: webCLIServlet,
						load: webcli.logs._callback,
						error: webcli.servletError,
						preventCache: true,
						content: {request: "getLogs"}
					});
				}
			} else {
				//some error occured on previous server request, stop automatic refreshing
				webcli.logDebug("logs.refresh: 'webcli.doRefresh!=true' abort automatic refresh");
				window.clearInterval(webcli.logs.refreshHandler);
			}
		}
		
	}
};

dojo.addOnLoad(function(){ 
	dojo.xhrGet({
		url: webCLIServlet,
		load: webcli.commands._callbackKnownCommands,
		error: webcli.servletError,
		content: {request: "getKnownCommands"}
	});
	webcli.logDebug("adding playButton");
	webcli.playButton = new dijit.form.Button({
		label: "Refresh",
		onClick: webcli.toggleRefresh,
		iconClass: "webcliEditorIcon webcliEditorIconPlay"
	});
	webcli.logDebug("adding pauseButton");
	webcli.pauseButton = new dijit.form.Button({
		label: "Stop Refresh",
		onClick: webcli.toggleRefresh,
		iconClass: "webcliEditorIcon webcliEditorIconPause"
	});
	webcli.logDebug("adding pauseButton to toolbar");
	var insertPosition;
	//find insert position after clear logs
	webcli.initRefreshPos("toolbar.clear");
	webcli.logDebug("webcli.refreshPos="+webcli.refreshPos);
	dijit.registry.byId("toolbar").addChild(webcli.pauseButton, webcli.refreshPos);
	webcli.startRefresh();
});
