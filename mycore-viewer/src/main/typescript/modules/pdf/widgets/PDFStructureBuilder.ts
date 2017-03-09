/// <reference path="../definitions/pdf.d.ts" />
/// <reference path="PDFStructureModel.ts" />


module mycore.viewer.widgets.pdf {
    import StructureChapter = mycore.viewer.model.StructureChapter;
    export class PDFStructureBuilder {

        constructor(private _document:PDFDocumentProxy, private _name:string) {
            this._pageCount = <any>(this._document.numPages);
        }

        private _structureModel: PDFStructureModel = null;
        private _chapterPageMap:MyCoReMap<string, model.StructureImage> = new MyCoReMap<String, mycore.viewer.model.StructureImage>();
        private _pages:Array<model.StructureImage> = new Array<model.StructureImage>();
        private _pageCount:number = 0;
        private _refPageMap:MyCoReMap<string, PDFPageProxy> = new MyCoReMap<String, PDFPageProxy>();
        private _idPageMap:MyCoReMap<number, model.StructureImage> = new MyCoReMap<Number, mycore.viewer.model.StructureImage>();
        private _loadedPageCount:number;
        private _outline:Array<PDFTreeNode>;
        private _rootChapter:model.StructureChapter;
        private _promise:ViewerPromise<PDFStructureModel, any> = new ViewerPromise<PDFStructureModel, any>();
        private _outlineTodoCount = 0;
        private static PDF_TEXT_HREF = "pdfText";


        public resolve() {
            this._resolvePages();
            this._resolveOutline();
            return <GivenViewerPromise<PDFStructureModel, any>>this._promise;
        }

        private _resolvePages() {
            var that = this;
            this._loadedPageCount = 0;

            for (var i = 1; i <= that._pageCount; i++) {
                var callback = this._createThumbnailDrawer(i);
                var additionalHref = new MyCoReMap<string,string>();
                additionalHref.set(PDFStructureBuilder.PDF_TEXT_HREF, i + "");
                var structureImage = new model.StructureImage("pdfPage", i + "", i, null, i + "", "pdfPage", callback, additionalHref);
                that._pages.push(structureImage);
                that._idPageMap.set(i, structureImage);
            }
        }

        private _createThumbnailDrawer(i) {
            var that = this;
            var imgData = null;
            var collectedCallbacks = new Array<(string)=>void>();
            return (callback:(string)=>void)=> {
                if (imgData == null) {
                    collectedCallbacks.push(callback);
                    if (collectedCallbacks.length == 1) {
                        that._document.getPage(i).then((page) => {
                            that._renderPage(collectedCallbacks, page);
                        });
                    }
                } else {
                    callback(imgData);
                }
            }
        }

        private _renderPage(callbacks:Array<(string)=>void>, page) {
            var originalSize =  new Size2D(page.view[2] - page.view[0], page.view[3] - page.view[1]);//IviewPDFCanvas.getPageSize(page);
            var largest = Math.max(originalSize.width, originalSize.height);
            var vpScale = 256 / largest;
            var vp = page.getViewport(vpScale);
            var thumbnailDrawCanvas = document.createElement("canvas");
            var thumbnailCanvasCtx = thumbnailDrawCanvas.getContext("2d");
            thumbnailDrawCanvas.width = (originalSize.width) * vpScale;
            thumbnailDrawCanvas.height = (originalSize.height) * vpScale;

            var task = <any> page.render({canvasContext: thumbnailCanvasCtx, viewport: vp})
            task.promise.then((onErr)=>{
                this._loadedPageCount++;
                let imgUrl = thumbnailDrawCanvas.toDataURL();
                thumbnailDrawCanvas = null;
                thumbnailCanvasCtx = null;
                for (var callbackIndex in callbacks) {
                    let callback = callbacks[callbackIndex];
                    callback(imgUrl);
                }
            });

        }

        private _resolveOutline() {
            var that = this;
            this._document.getOutline().then(function (nodes:Array<PDFTreeNode>) {
                that._outline = nodes;
                that.resolveStructure();
            });
        }


        private getChapterFromOutline(parent:model.StructureChapter, nodes:Array<PDFTreeNode>):Array<model.StructureChapter> {
            let chapterArr = new Array<model.StructureChapter>();
            for (let nodeIndex in nodes) {
                let currentNode = nodes[nodeIndex];
                let destResolver = ((copyChapter) => (callback) => {
                    let promise;
                    if (typeof copyChapter.dest === 'string') {
                        promise = this._document.getDestination(copyChapter.dest);
                    } else {
                        promise = (<any>window).Promise.resolve(copyChapter.dest);
                    }


                    promise.then((destination) => {
                        if (!(destination instanceof Array)) {
                            console.error("Invalid destination " + destination);
                            return;
                        } else {
                            this._document.getPageIndex(destination[ 0 ]).then((pageNumber) => {
                                if (typeof pageNumber != "undefined" && pageNumber != null) {
                                    if (pageNumber > this._pageCount) {
                                        console.error("Destination outside of Document! (" + pageNumber + ")");
                                    } else {
                                        callback(pageNumber + 1);
                                    }
                                }
                            });
                        }
                    });
                })(currentNode);
                let chapter = new model.StructureChapter(parent, "pdfChapter", Utils.hash(currentNode.title).toString(), currentNode.title, null, null,destResolver);
                let children = this.getChapterFromOutline(chapter, currentNode.items);
                chapter.chapter = children;
                chapterArr.push(chapter);
            }

            return chapterArr;
        }

        private checkResolvable() {
            if (this._structureModel != null && this._outlineTodoCount == 0)
                this._promise.resolve(this._structureModel);
        }

        /**
         * Checks if all needed data is resolved and the structure model can be build.
         * Executes the Callback.
         */
        private resolveStructure() {
            if (typeof this._outline != "undefined") {
                var that = this;
                this._rootChapter = new model.StructureChapter(null, "pdf", "0", this._name, null, null, () => 1);
                this._rootChapter.chapter = this.getChapterFromOutline(this._rootChapter, this._outline);
                this._structureModel = new PDFStructureModel(this._rootChapter, this._pages, this._chapterPageMap, new MyCoReMap<string, mycore.viewer.model.StructureChapter>(), this._refPageMap);
                this.checkResolvable();
            }
        }

        /**
         * Converts a destination to a String.
         * @param ref the PDFRef wich should be converted.
         * @returns {string}
         */
        private static destToString(ref:PDFRef):string {
            return   ref.gen + " " + ref.num;

        }

    }
}
