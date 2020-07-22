function CordovaTrezor() { };

CordovaTrezor.prototype.getPublicKeys = function (request, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "CordovaTrezor", "getPublicKeys", [request]);
}

module.exports = new CordovaTrezor();