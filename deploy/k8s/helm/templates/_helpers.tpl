{{/*
  Helm 模板辅助函数
*/}}

{{- define "app.name" -}}
{{- default .Chart.Name .Values.app.name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "app.fullname" -}}
{{- $name := default .Chart.Name .Values.app.name -}}
{{- $fullname := printf "%s-%s" .Release.Name $name -}}
{{- default $fullname .Values.app.fullname | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "app.namespace" -}}
{{- default .Values.namespace .Release.Namespace }}
{{- end }}

{{- define "app.version" -}}
{{- .Values.global.imageTag | default .Chart.AppVersion | toString }}
{{- end }}

{{- define "app.mysql.dsn" -}}
{{- if .Values.mysql.enabled -}}
{{- printf "integration-mysql" }}
{{- else -}}
{{- .Values.app.externalDatabase.host | quote }}
{{- end }}
{{- end }}

{{- define "app.mysql.secret" -}}
{{- if .Values.mysql.enabled -}}
{{- printf "%s" "integration-secrets" }}
{{- else -}}
{{- printf "integration-secrets-external" }}
{{- end }}
{{- end }}
