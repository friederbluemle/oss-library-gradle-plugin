package com.auth0.gradle.oss

import com.auth0.gradle.oss.extensions.Library
import com.auth0.gradle.oss.extensions.Developer
import com.auth0.gradle.oss.tasks.ChangeLogTask
import com.auth0.gradle.oss.tasks.ReadmeTask
import com.auth0.gradle.oss.tasks.ReleaseTask
import com.auth0.gradle.oss.versioning.Semver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.Artifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

class LibraryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('oss', Library)
        project.oss.extensions.developers = project.container(Developer)
        project.pluginManager.apply('maven-publish')
        java(project)
        maven(project)
        release(project)
    }

    private void java(Project project) {
        project.pluginManager.apply('java')
        project.configure(project) {
            task('sourcesJar', type: Jar, dependsOn: 'classes') {
                classifier = 'sources'
                from sourceSets.main.allSource
            }
            task('javadocJar', type: Jar, dependsOn: javadoc) {
                classifier = 'javadoc'
                from javadoc.getDestinationDir()
            }

            artifacts {
                archives sourcesJar, javadocJar
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        Artifact sourcesJar
                        Artifact javadocJar
                        groupId project.group
                        artifactId project.name
                        version project.version
                    }
                }
            }
        }
    }

    private void maven(Project project, String packaging = 'jar') {
        def lib = project.extensions.oss
        project.configure(project) {
            publishing.publications.all {
                pom.withXml {
                    def root = asNode()

                    root.appendNode('packaging', packaging)
                    root.appendNode('name', lib.name)
                    root.appendNode('description', lib.description)

                    def developersNode = root.appendNode('developers')
                    project.extensions.oss.developers.each {
                        def node = developersNode.appendNode('developer')
                        node.appendNode('id', it.id)
                        node.appendNode('name', it.name)
                        node.appendNode('email', it.email)
                    }

                    def dependenciesNode = root.appendNode('dependencies')

                    configurations.compile.allDependencies.each {
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', it.group)
                        dependencyNode.appendNode('artifactId', it.name)
                        dependencyNode.appendNode('version', it.version)
                    }

                    def licenceNode = root.appendNode('licenses').appendNode('license')
                    licenceNode.appendNode('name', 'The MIT License (MIT)')
                    licenceNode.appendNode('url', "https://raw.githubusercontent.com/${lib.organization}/${lib.repository}/master/LICENSE")
                    licenceNode.appendNode('distribution', 'repo')

                    def scmNode = root.appendNode('scm')
                    scmNode.appendNode('connection', "scm:git@github.com:${lib.organization}/${lib.repository}.git")
                    scmNode.appendNode('developerConnection', "scm:git@github.com:${lib.organization}/${lib.repository}.git")
                    scmNode.appendNode('url', "https://github.com/${lib.organization}/${lib.repository}")
                }
            }
        }
    }

    private void release(Project project) {
        def semver = Semver.current()
        project.version = semver.stringVersion
        def version = semver.version
        def nextMinor = semver.nextMinor()
        def nextPatch = semver.nextPatch()
        project.task('changelogMinor', type: ChangeLogTask) {
            current = version
            next = nextMinor
        }
        project.task('changelogPatch', type: ChangeLogTask) {
            current = version
            next = nextPatch
        }
        project.task('readmeMinor', type: ReadmeTask, dependsOn: 'changelogMinor') {
            current = version
            next = nextMinor
        }
        project.task('readmePatch', type: ReadmeTask, dependsOn: 'changelogPatch') {
            current = version
            next = nextPatch
        }
        project.task('releaseMinor', type: ReleaseTask, dependsOn: 'readmeMinor') {
            tagName = nextMinor
        }
        project.task('releasePatch', type: ReleaseTask, dependsOn: 'readmePatch') {
            tagName = nextPatch
        }
    }
}