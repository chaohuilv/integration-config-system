using System;

namespace IntegrationSDK
{
    /// <summary>
    /// SDK 调用异常
    /// </summary>
    public class IntegrationException : Exception
    {
        public IntegrationException(string message) : base(message) { }

        public IntegrationException(string message, Exception innerException)
            : base(message, innerException) { }
    }
}
